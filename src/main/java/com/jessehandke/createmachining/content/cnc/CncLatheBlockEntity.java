package com.jessehandke.createmachining.content.cnc;

import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.jessehandke.createmachining.content.lathe.LatheLogic;
import com.jessehandke.createmachining.content.lathe.TurningRecipe;
import com.jessehandke.createmachining.content.quality.QualityModel;
import com.jessehandke.createmachining.registry.ModBlocks;
import com.jessehandke.createmachining.registry.ModItems;
import com.jessehandke.createmachining.registry.ModRecipeTypes;
import com.jessehandke.createmachining.registry.ModTags;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.item.ItemHelper;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;

/**
 * The CNC Lathe's brain, living in the controller block. One cycle runs:
 *
 *   IDLE  -> CLOSING (door slides shut) -> CUTTING (turret plunges and traverses) -> OPENING -> IDLE
 *
 * Same turning recipes and quality rules as the Bench Lathe (via LatheLogic), but it cuts twice
 * as fast, keeps chips in a tray instead of mixing them with parts, and has dedicated ports.
 *
 * The server runs the real state machine. The client only advances the timers between updates
 * so the animation stays smooth; every phase change is sent from the server with sendData().
 */
public class CncLatheBlockEntity extends KineticBlockEntity {

    public enum Phase { IDLE, CLOSING, CUTTING, OPENING }

    /** Ticks for the door to slide fully open or shut. */
    public static final int DOOR_TICKS = 12;
    /** CNC rapids and feeds: cycles run this many times faster than the Bench Lathe. */
    public static final int SPEED_MULTIPLIER = 2;
    /** How often (ticks) the chip conveyor moves chips out. */
    private static final int CONVEYOR_INTERVAL = 10;

    public final ItemStackHandler stockInv = new ItemStackHandler(1);
    public final ItemStackHandler toolInv = new ItemStackHandler(1) {
        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }
    };
    public final ItemStackHandler partsInv = new ItemStackHandler(4);
    /** The chip tray. When it's full the machine stops until someone empties it (or a conveyor does). */
    public final ItemStackHandler chipInv = new ItemStackHandler(2);

    // Port views: each one only allows what makes sense at that spot of the machine
    private final IItemHandler stockPort = new PortHandler(stockInv, true, false, this::isValidStock);
    private final IItemHandler toolPort = new PortHandler(toolInv, true, false, s -> s.is(ModItems.CARBIDE_INSERT.get()));
    private final IItemHandler partsPort = new PortHandler(partsInv, false, true, s -> false);
    private final IItemHandler chipPort = new PortHandler(chipInv, false, true, s -> false);

    public Phase phase = Phase.IDLE;
    /** Ticks spent in the current phase (used by the door animation). */
    public int phaseTicks;
    /** Cutting time left, and the cut's total length (for the turret's traverse animation). */
    public int timer, cutTotal = 1;
    public boolean conveyorInstalled;
    /** Coolant is always on for now. Later it'll need a fluid supply through Create's pipes. */
    public boolean coolantOn = true;

    private float cycleSpeedFactor = 1;
    private TurningRecipe lastRecipe;
    private int conveyorCooldown;

    public CncLatheBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** Render bounds cover the whole machine, not just the controller block. */
    @Override
    protected net.minecraft.world.phys.AABB createRenderBoundingBox() {
        return new net.minecraft.world.phys.AABB(worldPosition).inflate(3);
    }

    public Direction front() {
        return getBlockState().getValue(CncLatheBlock.HORIZONTAL_FACING);
    }

    // ---------------------------------------------------------------------------------------------
    // Ports: funnels, hoppers and chutes find these through the structural blocks
    // ---------------------------------------------------------------------------------------------

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlock(Capabilities.ItemHandler.BLOCK, (level, pos, state, be, side) -> {
            CncLatheBlockEntity lathe = CncLatheStructuralBlock.controller(level, pos, state);
            return lathe == null ? null : lathe.portFor(state.getValue(CncLatheStructuralBlock.PART));
        }, ModBlocks.CNC_LATHE_STRUCTURAL.get());
    }

    @Nullable
    public IItemHandler portFor(CncLathePart part) {
        return switch (part) {
            case CHUCK -> stockPort;                                   // bar feeder / funnel in
            case TURRET -> toolPort;                                   // tooling in
            case PARTS_CATCHER -> partsPort;                           // parts out
            case CHIP_OUTLET -> conveyorInstalled ? chipPort : null;   // chips out, needs the conveyor
            default -> null;
        };
    }

    // ---------------------------------------------------------------------------------------------
    // The cycle
    // ---------------------------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();

        if (level.isClientSide) {
            clientTick();
            return;
        }

        if (conveyorInstalled && --conveyorCooldown <= 0) {
            conveyorCooldown = CONVEYOR_INTERVAL;
            runChipConveyor();
        }

        if (getSpeed() == 0)
            return; // no power: everything freezes where it is

        switch (phase) {
            case IDLE -> {
                if (canStartCycle()) {
                    setPhase(Phase.CLOSING);
                    level.playSound(null, worldPosition, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 0.6f, 1.4f);
                }
            }
            case CLOSING -> {
                if (++phaseTicks >= DOOR_TICKS) {
                    TurningRecipe recipe = findRecipe();
                    if (recipe == null) { // stock was removed somehow; open back up
                        setPhase(Phase.OPENING);
                        return;
                    }
                    cutTotal = timer = Math.max(1, recipe.getProcessingDuration());
                    cycleSpeedFactor = 1;
                    setPhase(Phase.CUTTING);
                }
            }
            case CUTTING -> {
                if (lastRecipe != null)
                    cycleSpeedFactor = Math.min(cycleSpeedFactor,
                            QualityModel.speedFactor(getSpeed(), lastRecipe.getMinRpm(), lastRecipe.getMaxRpm()));
                timer -= getProcessingSpeed();
                if (timer <= 0) {
                    finishPart();
                    setPhase(Phase.OPENING);
                    level.playSound(null, worldPosition, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 0.6f, 1.4f);
                }
            }
            case OPENING -> {
                if (++phaseTicks >= DOOR_TICKS)
                    setPhase(Phase.IDLE);
            }
        }
    }

    /** Client side: keep the animation counters moving between server updates, and make particles. */
    private void clientTick() {
        if (getSpeed() == 0)
            return;
        switch (phase) {
            case CLOSING, OPENING -> phaseTicks = Math.min(phaseTicks + 1, DOOR_TICKS);
            case CUTTING -> {
                timer = Math.max(0, timer - getProcessingSpeed());
                spawnCuttingParticles();
            }
            default -> {}
        }
    }

    private void setPhase(Phase next) {
        phase = next;
        phaseTicks = 0;
        sendData();
        setChanged();
    }

    private boolean canStartCycle() {
        return !stockInv.getStackInSlot(0).isEmpty()
                && !toolInv.getStackInSlot(0).isEmpty()
                && !LatheLogic.isFull(partsInv)
                && !LatheLogic.isFull(chipInv)
                && findRecipe() != null;
    }

    public int getProcessingSpeed() {
        return SPEED_MULTIPLIER * Mth.clamp((int) Math.abs(getSpeed() / 16f), 1, 512);
    }

    @Nullable
    private TurningRecipe findRecipe() {
        RecipeWrapper input = new RecipeWrapper(stockInv);
        if (lastRecipe != null && lastRecipe.matches(input, level))
            return lastRecipe;
        Optional<RecipeHolder<TurningRecipe>> found = ModRecipeTypes.TURNING.find(input, level);
        lastRecipe = found.map(RecipeHolder::value).orElse(null);
        return lastRecipe;
    }

    private void finishPart() {
        TurningRecipe recipe = findRecipe();
        ItemStack tool = toolInv.getStackInSlot(0);
        if (recipe == null || tool.isEmpty())
            return;

        int processScore = LatheLogic.processScore(cycleSpeedFactor, tool);
        ItemStack stock = stockInv.getStackInSlot(0);
        stock.shrink(1);
        stockInv.setStackInSlot(0, stock);

        // Parts go to the parts catcher, everything else (swarf) falls into the chip tray
        for (ItemStack result : recipe.rollResults(level.random)) {
            if (result.is(ModTags.MACHINED_PARTS))
                LatheLogic.insertIntoLot(partsInv, result, processScore);
            else
                ItemHandlerHelper.insertItemStacked(chipInv, result, false);
        }
        LatheLogic.wearTool(toolInv, QualityModel.toolWearPerPart(getSpeed(), recipe.getMaxRpm()), level, worldPosition);
    }

    // ---------------------------------------------------------------------------------------------
    // Chip conveyor: pushes chips out of the tailstock end of the machine
    // ---------------------------------------------------------------------------------------------

    private void runChipConveyor() {
        int slot = firstNonEmpty(chipInv);
        if (slot < 0)
            return;
        Direction out = CncLatheLayout.lengthDir(front());
        BlockPos outlet = CncLatheLayout.posOf(CncLathePart.CHIP_OUTLET, CncLatheLayout.origin(worldPosition, front()), front());
        BlockPos target = outlet.relative(out);
        ItemStack one = chipInv.getStackInSlot(slot).copyWithCount(1);

        // Anything with an inventory (chest, funnel, chute, another machine)...
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, target, out.getOpposite());
        ItemStack left = handler == null ? one : ItemHandlerHelper.insertItemStacked(handler, one, false);
        // ...or a Create belt / depot
        if (!left.isEmpty()) {
            DirectBeltInputBehaviour belt = BlockEntityBehaviour.get(level, target, DirectBeltInputBehaviour.TYPE);
            if (belt != null && belt.canInsertFromSide(out))
                left = belt.handleInsertion(left, out, false);
        }
        if (left.isEmpty()) {
            chipInv.extractItem(slot, 1, false);
            setChanged();
        }
    }

    private static int firstNonEmpty(ItemStackHandler inv) {
        for (int i = 0; i < inv.getSlots(); i++)
            if (!inv.getStackInSlot(i).isEmpty())
                return i;
        return -1;
    }

    // ---------------------------------------------------------------------------------------------
    // Right-click interactions, by machine part
    // ---------------------------------------------------------------------------------------------

    public ItemInteractionResult use(CncLathePart part, ItemStack stack, Player player, InteractionHand hand) {
        boolean client = level.isClientSide;
        return switch (part) {
            case CHUCK, WORK_ZONE -> useChuck(stack, player, hand, client);
            case TURRET -> useTurret(stack, player, hand, client);
            case CHIP_TRAY -> useChipTray(stack, player, hand, client);
            default -> ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        };
    }

    /** Safety interlock: the door stays shut while the machine is running. */
    private boolean doorLocked(Player player, boolean client) {
        if (phase == Phase.IDLE)
            return false;
        if (client)
            player.displayClientMessage(Component.translatable("create_machining.cnc_lathe.door_locked")
                    .withStyle(ChatFormatting.GOLD), true);
        return true;
    }

    private ItemInteractionResult useChuck(ItemStack stack, Player player, InteractionHand hand, boolean client) {
        if (stack.isEmpty() || isValidStock(stack)) {
            if (doorLocked(player, client) || client)
                return ItemInteractionResult.SUCCESS;
        } else {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (!stack.isEmpty()) { // load stock
            player.setItemInHand(hand, stockPort.insertItem(0, stack.copy(), false));
        } else if (!giveAll(partsInv, player)) { // pull parts; if none, pull the stock back out
            player.getInventory().placeItemBackInInventory(stockInv.getStackInSlot(0));
            stockInv.setStackInSlot(0, ItemStack.EMPTY);
        }
        changed();
        return ItemInteractionResult.SUCCESS;
    }

    private ItemInteractionResult useTurret(ItemStack stack, Player player, InteractionHand hand, boolean client) {
        boolean isInsert = stack.is(ModItems.CARBIDE_INSERT.get());
        if (!stack.isEmpty() && !isInsert)
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (doorLocked(player, client) || client)
            return ItemInteractionResult.SUCCESS;

        if (isInsert) {
            player.setItemInHand(hand, toolPort.insertItem(0, stack.copy(), false));
        } else {
            player.getInventory().placeItemBackInInventory(toolInv.getStackInSlot(0));
            toolInv.setStackInSlot(0, ItemStack.EMPTY);
        }
        changed();
        return ItemInteractionResult.SUCCESS;
    }

    private ItemInteractionResult useChipTray(ItemStack stack, Player player, InteractionHand hand, boolean client) {
        // Mechanical Belt in hand: install the chip conveyor
        if (stack.is(AllItems.BELT_CONNECTOR.get())) {
            if (conveyorInstalled)
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            if (client)
                return ItemInteractionResult.SUCCESS;
            conveyorInstalled = true;
            if (!player.getAbilities().instabuild)
                stack.shrink(1);
            level.playSound(null, worldPosition, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 1, 0.8f);
            invalidatePorts();
            changed();
            return ItemInteractionResult.SUCCESS;
        }
        // Empty hand: scoop the chips out by hand
        if (stack.isEmpty()) {
            if (!client) {
                giveAll(chipInv, player);
                changed();
            }
            return ItemInteractionResult.SUCCESS;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** Move everything in an inventory to the player. Returns false if it was empty. */
    private static boolean giveAll(ItemStackHandler inv, Player player) {
        boolean any = false;
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (stack.isEmpty())
                continue;
            any = true;
            player.getInventory().placeItemBackInInventory(stack);
            inv.setStackInSlot(i, ItemStack.EMPTY);
        }
        return any;
    }

    private void changed() {
        setChanged();
        sendData();
    }

    /** Ports changed (e.g. conveyor installed): tell neighbours to look up our capabilities again. */
    private void invalidatePorts() {
        level.invalidateCapabilities(CncLatheLayout.posOf(CncLathePart.CHIP_OUTLET,
                CncLatheLayout.origin(worldPosition, front()), front()));
    }

    public boolean isValidStock(ItemStack stack) {
        ItemStackHandler tester = new ItemStackHandler(1);
        tester.setStackInSlot(0, stack);
        RecipeWrapper input = new RecipeWrapper(tester);
        if (lastRecipe != null && lastRecipe.matches(input, level))
            return true;
        return ModRecipeTypes.TURNING.find(input, level).isPresent();
    }

    // ---------------------------------------------------------------------------------------------
    // Animation helpers for the renderer (all 0..1)
    // ---------------------------------------------------------------------------------------------

    /** 1 = door fully open, 0 = shut. */
    public float doorOpenness(float partialTicks) {
        float t = Mth.clamp((phaseTicks + (getSpeed() == 0 ? 0 : partialTicks)) / DOOR_TICKS, 0, 1);
        return switch (phase) {
            case IDLE -> 1;
            case CLOSING -> 1 - t;
            case CUTTING -> 0;
            case OPENING -> t;
        };
    }

    /** How far through the cut we are: 0 at the start, 1 at the end. Only meaningful while CUTTING. */
    public float cutProgress(float partialTicks) {
        if (phase != Phase.CUTTING)
            return 0;
        float remaining = Math.max(0, timer - (getSpeed() == 0 ? 0 : partialTicks * getProcessingSpeed()));
        return Mth.clamp(1 - remaining / cutTotal, 0, 1);
    }

    // ---------------------------------------------------------------------------------------------
    // Particles (client)
    // ---------------------------------------------------------------------------------------------

    private void spawnCuttingParticles() {
        ItemStack stock = stockInv.getStackInSlot(0);
        if (stock.isEmpty())
            return;
        Direction front = front();
        float cutL = CncLatheGeometry.cutPositionL(cutProgress(0));
        Vec3 cut = CncLatheLayout.worldPoint(worldPosition, front, cutL,
                CncLatheGeometry.SPINDLE_H, CncLatheGeometry.CUT_D);

        // Chips flying off the cut
        if (level.random.nextInt(2) == 0) {
            Vec3 v = new Vec3(level.random.nextGaussian() * .05, .05 + level.random.nextDouble() * .08, level.random.nextGaussian() * .05);
            level.addParticle(new ItemParticleOption(ParticleTypes.ITEM, stock), cut.x, cut.y, cut.z, v.x, v.y, v.z);
        }
        // Coolant: a stream falling from the manifold above the cut, splashing where it hits.
        // (Splash particles pick their own upward bounce, so they're only spawned at the cut,
        // low enough that the enclosure keeps them in.)
        if (coolantOn) {
            for (int i = 0; i < 2; i++) {
                Vec3 stream = CncLatheLayout.worldPoint(worldPosition, front,
                        cutL + level.random.nextGaussian() * .5, 28 - level.random.nextFloat() * 3, CncLatheGeometry.CUT_D + 1);
                level.addParticle(ParticleTypes.FALLING_WATER, stream.x, stream.y, stream.z, 0, 0, 0);
            }
            if (level.random.nextInt(3) == 0)
                level.addParticle(ParticleTypes.SPLASH, cut.x, cut.y - .05, cut.z, 0, 0, 0);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Goggles
    // ---------------------------------------------------------------------------------------------

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        tooltip.add(line(Component.translatable("create_machining.goggles.cnc_header").withStyle(ChatFormatting.WHITE)));
        tooltip.add(line(Component.translatable("create_machining.goggles.phase." + phase.name().toLowerCase())
                .withStyle(phase == Phase.IDLE ? ChatFormatting.GRAY : ChatFormatting.AQUA)));

        ItemStack tool = toolInv.getStackInSlot(0);
        if (tool.isEmpty()) {
            tooltip.add(line(Component.translatable("create_machining.goggles.no_tool").withStyle(ChatFormatting.RED)));
        } else {
            int life = Math.round(100f * (tool.getMaxDamage() - tool.getDamageValue()) / tool.getMaxDamage());
            tooltip.add(line(Component.translatable("create_machining.goggles.tool_life", life)
                    .withStyle(life > 25 ? ChatFormatting.GRAY : ChatFormatting.GOLD)));
        }

        TurningRecipe recipe = stockInv.getStackInSlot(0).isEmpty() ? null : findRecipe();
        if (recipe != null) {
            int rpm = Math.round(Math.abs(getSpeed()));
            String key = rpm < recipe.getMinRpm() ? "too_slow" : rpm > recipe.getMaxRpm() ? "too_fast" : "in_band";
            tooltip.add(line(Component.translatable("create_machining.goggles.ideal_speed",
                    recipe.getMinRpm(), recipe.getMaxRpm()).withStyle(ChatFormatting.GRAY)));
            tooltip.add(line(Component.translatable("create_machining.goggles." + key, rpm)
                    .withStyle(key.equals("in_band") ? ChatFormatting.GREEN : ChatFormatting.GOLD)));
        }

        int chips = 0, chipCap = 0;
        for (int i = 0; i < chipInv.getSlots(); i++) {
            chips += chipInv.getStackInSlot(i).getCount();
            chipCap += chipInv.getSlotLimit(i);
        }
        tooltip.add(line(Component.translatable("create_machining.goggles.chip_tray", chips, chipCap)
                .withStyle(chips >= chipCap ? ChatFormatting.RED : ChatFormatting.GRAY)));
        tooltip.add(line(Component.translatable(conveyorInstalled
                ? "create_machining.goggles.conveyor_on" : "create_machining.goggles.conveyor_off")
                .withStyle(ChatFormatting.DARK_GRAY)));
        tooltip.add(line(Component.translatable("create_machining.goggles.coolant_on").withStyle(ChatFormatting.DARK_AQUA)));
        return true;
    }

    private static Component line(Component text) {
        return Component.literal("    ").append(text);
    }

    // ---------------------------------------------------------------------------------------------
    // Saving, syncing, breaking
    // ---------------------------------------------------------------------------------------------

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        tag.putString("Phase", phase.name());
        tag.putInt("PhaseTicks", phaseTicks);
        tag.putInt("Timer", timer);
        tag.putInt("CutTotal", cutTotal);
        tag.putFloat("CycleSpeedFactor", cycleSpeedFactor);
        tag.putBoolean("Conveyor", conveyorInstalled);
        tag.put("Stock", stockInv.serializeNBT(registries));
        tag.put("Tool", toolInv.serializeNBT(registries));
        tag.put("Parts", partsInv.serializeNBT(registries));
        tag.put("Chips", chipInv.serializeNBT(registries));
        super.write(tag, registries, clientPacket);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        try {
            phase = Phase.valueOf(tag.getString("Phase"));
        } catch (IllegalArgumentException e) {
            phase = Phase.IDLE;
        }
        phaseTicks = tag.getInt("PhaseTicks");
        timer = tag.getInt("Timer");
        cutTotal = Math.max(1, tag.getInt("CutTotal"));
        cycleSpeedFactor = tag.contains("CycleSpeedFactor") ? tag.getFloat("CycleSpeedFactor") : 1;
        boolean hadConveyor = conveyorInstalled;
        conveyorInstalled = tag.getBoolean("Conveyor");
        stockInv.deserializeNBT(registries, tag.getCompound("Stock"));
        toolInv.deserializeNBT(registries, tag.getCompound("Tool"));
        partsInv.deserializeNBT(registries, tag.getCompound("Parts"));
        chipInv.deserializeNBT(registries, tag.getCompound("Chips"));
        super.read(tag, registries, clientPacket);
        if (hadConveyor != conveyorInstalled && level != null && !level.isClientSide)
            invalidatePorts();
    }

    @Override
    public void destroy() {
        super.destroy();
        ItemHelper.dropContents(level, worldPosition, stockInv);
        ItemHelper.dropContents(level, worldPosition, toolInv);
        ItemHelper.dropContents(level, worldPosition, partsInv);
        ItemHelper.dropContents(level, worldPosition, chipInv);
        if (conveyorInstalled) {
            Vec3 c = Vec3.atCenterOf(worldPosition);
            level.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(level, c.x, c.y, c.z,
                    new ItemStack(AllItems.BELT_CONNECTOR.get())));
        }
    }

    /**
     * One inventory as seen from a port: insert-only or extract-only, with a filter for what's
     * allowed in. Wrapping the real inventory like this is the "view" pattern: no copying,
     * every change goes straight through.
     */
    private record PortHandler(ItemStackHandler inv, boolean canInsert, boolean canExtract,
                               java.util.function.Predicate<ItemStack> filter) implements IItemHandler {
        @Override
        public int getSlots() {
            return inv.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return inv.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (!canInsert || !filter.test(stack))
                return stack;
            return inv.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return canExtract ? inv.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return inv.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return canInsert && filter.test(stack);
        }
    }
}
