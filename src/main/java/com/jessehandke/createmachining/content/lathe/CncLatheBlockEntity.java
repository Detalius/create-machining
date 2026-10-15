package com.jessehandke.createmachining.content.lathe;

import java.util.List;
import java.util.Optional;

import com.jessehandke.createmachining.content.quality.QualityModel;
import com.jessehandke.createmachining.registry.ModBlockEntities;
import com.jessehandke.createmachining.registry.ModDataComponents;
import com.jessehandke.createmachining.registry.ModItems;
import com.jessehandke.createmachining.registry.ModRecipeTypes;
import com.jessehandke.createmachining.registry.ModTags;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.item.ItemHelper;
import com.simibubi.create.foundation.sound.SoundScapes;
import com.simibubi.create.foundation.sound.SoundScapes.AmbienceGroup;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.CombinedInvWrapper;
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;

/**
 * One placed lathe: its inventories, its cycle timer, and the logic that runs every game tick
 * (20 times per second). Think of it as an instance of a struct with a tick() function the game
 * calls on a loop. KineticBlockEntity is Create's base class that already tracks the spindle
 * speed (RPM) and stress for us.
 *
 * Modelled on Create's MillstoneBlockEntity.
 */
public class CncLatheBlockEntity extends KineticBlockEntity {
    /** Countdown value used while waiting on stock that has no turning recipe. */
    private static final int IDLE_RECHECK = 100;

    // ItemStackHandler is NeoForge's ready-made inventory: an array of item stacks.
    public final ItemStackHandler inputInv = new ItemStackHandler(1);
    public final ItemStackHandler toolInv = new ItemStackHandler(1) {
        // an anonymous subclass: overriding one method inline, without naming a new class
        @Override
        public int getSlotLimit(int slot) {
            return 1; // one insert in the holder at a time
        }
    };
    public final ItemStackHandler outputInv = new ItemStackHandler(4);
    // The view of our inventories that hoppers, funnels and belts get to see.
    private final IItemHandler automationHandler = new LatheInventoryHandler();

    /** Remaining cycle time. Counts down faster at higher RPM; the part is done when it hits 0. */
    public int timer;
    /**
     * Worst speed factor seen during the current cycle. Tracking the worst (not the final) value
     * means you can't run the whole cycle at max RPM and then slow down right before it ends.
     */
    private float cycleSpeedFactor = 1;
    /** Cached so we don't search every recipe in the game on every tick. */
    private TurningRecipe lastRecipe;

    public CncLatheBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /**
     * Capabilities are how other blocks find our inventory: "does the block at this position
     * have an item handler?" We answer with automationHandler.
     */
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.CNC_LATHE.get(),
                (be, side) -> be.automationHandler);
    }

    /** Behaviours are Create's plug-in components. This one lets a belt feed stock straight in. */
    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        behaviours.add(new DirectBeltInputBehaviour(this));
        super.addBehaviours(behaviours);
    }

    // ---------------------------------------------------------------------------------------------
    // The machining cycle
    // ---------------------------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick(); // let Create do its kinetic bookkeeping first

        if (getSpeed() == 0)
            return; // spindle stopped
        if (toolInv.getStackInSlot(0).isEmpty())
            return; // nothing to cut with

        // Parts bin full? Feed hold until someone empties it.
        for (int i = 0; i < outputInv.getSlots(); i++)
            if (outputInv.getStackInSlot(i).getCount() == outputInv.getSlotLimit(i))
                return;

        // Mid-cycle: count down
        if (timer > 0) {
            timer -= getProcessingSpeed();
            if (lastRecipe != null)
                cycleSpeedFactor = Math.min(cycleSpeedFactor,
                        QualityModel.speedFactor(getSpeed(), lastRecipe.getMinRpm(), lastRecipe.getMaxRpm()));
            if (level.isClientSide) {
                spawnChipParticles();
                return;
            }
            if (timer <= 0)
                finishPart();
            return;
        }

        // Idle: is there stock to start on?
        if (inputInv.getStackInSlot(0).isEmpty())
            return;

        TurningRecipe recipe = findRecipe();
        if (recipe == null) {
            timer = IDLE_RECHECK;
            sendData();
            return;
        }

        // Cycle start
        timer = recipe.getProcessingDuration();
        cycleSpeedFactor = 1;
        sendData();
    }

    /** The turning recipe for the current stock, or null if there isn't one. */
    private TurningRecipe findRecipe() {
        RecipeWrapper input = new RecipeWrapper(inputInv);
        if (lastRecipe != null && lastRecipe.matches(input, level))
            return lastRecipe;
        Optional<RecipeHolder<TurningRecipe>> found = ModRecipeTypes.TURNING.find(input, level);
        // Optional is a box that may be empty, so we don't have to pass nulls around.
        // .map(...) transforms what's inside if there is something; .orElse gives a fallback.
        lastRecipe = found.map(RecipeHolder::value).orElse(null);
        return lastRecipe;
    }

    /**
     * Higher RPM means a faster cycle. Same formula as the Millstone: 1 at low speed, up to 16
     * per tick at Create's max of 256 RPM. The catch is the quality penalty outside the band.
     */
    public int getProcessingSpeed() {
        return Mth.clamp((int) Math.abs(getSpeed() / 16f), 1, 512);
    }

    private void finishPart() {
        TurningRecipe recipe = findRecipe();
        ItemStack tool = toolInv.getStackInSlot(0);
        if (recipe == null || tool.isEmpty())
            return;

        // Quality is decided by the setup: speeds over the cycle, and tool condition.
        float toolLife = 1 - (float) tool.getDamageValue() / tool.getMaxDamage();
        int processScore = QualityModel.processScore(cycleSpeedFactor, QualityModel.toolFactor(toolLife));

        // Use up one piece of stock
        ItemStack stock = inputInv.getStackInSlot(0);
        stock.shrink(1);
        inputInv.setStackInSlot(0, stock);

        // rollResults rolls the dice for chance outputs (like swarf) and returns what came out.
        // Machined parts get the hidden process score; swarf doesn't.
        for (ItemStack result : recipe.rollResults(level.random)) {
            if (result.is(ModTags.MACHINED_PARTS))
                insertIntoLot(result, processScore);
            else
                ItemHandlerHelper.insertItemStacked(outputInv, result, false);
        }

        wearTool(QualityModel.toolWearPerPart(getSpeed(), recipe.getMaxRpm()));

        sendData();  // sync to clients
        setChanged(); // mark for saving
    }

    /**
     * Put fresh parts into the output as one lot. If the bin already holds uninspected parts of
     * the same kind, they're pooled into a single stack with a count-weighted average score
     * (like mixing parts in one tote), so they don't split into a stack per score.
     */
    private void insertIntoLot(ItemStack parts, int processScore) {
        for (int slot = 0; slot < outputInv.getSlots() && !parts.isEmpty(); slot++) {
            ItemStack lot = outputInv.getStackInSlot(slot);
            if (!lot.is(parts.getItem()) || !lot.has(ModDataComponents.PROCESS_SCORE))
                continue;
            int room = lot.getMaxStackSize() - lot.getCount();
            if (room <= 0)
                continue;
            int moved = Math.min(room, parts.getCount());
            int lotScore = lot.getOrDefault(ModDataComponents.PROCESS_SCORE, 0);
            int pooledScore = Math.round((lotScore * lot.getCount() + processScore * moved)
                    / (float) (lot.getCount() + moved));
            ItemStack merged = lot.copyWithCount(lot.getCount() + moved);
            merged.set(ModDataComponents.PROCESS_SCORE, pooledScore);
            outputInv.setStackInSlot(slot, merged);
            parts.shrink(moved);
        }
        if (!parts.isEmpty()) {
            parts.set(ModDataComponents.PROCESS_SCORE, processScore);
            ItemHandlerHelper.insertItemStacked(outputInv, parts, false);
        }
    }

    private void wearTool(int amount) {
        ItemStack tool = toolInv.getStackInSlot(0);
        int newDamage = tool.getDamageValue() + amount;
        if (newDamage >= tool.getMaxDamage()) {
            toolInv.setStackInSlot(0, ItemStack.EMPTY); // insert's done, chip it out
            level.playSound(null, worldPosition, SoundEvents.ITEM_BREAK, SoundSource.BLOCKS, 1, 1);
            return;
        }
        tool.setDamageValue(newDamage);
        toolInv.setStackInSlot(0, tool);
    }

    // ---------------------------------------------------------------------------------------------
    // Interaction helpers (used by CncLatheBlock when a player right-clicks)
    // ---------------------------------------------------------------------------------------------

    /** Load stock or a carbide insert. Returns whatever didn't fit, or the whole stack if neither. */
    public ItemStack insertItem(ItemStack stack) {
        ItemStack remainder = stack.copy();
        for (int slot = 0; slot < 2 && !remainder.isEmpty(); slot++) // slot 0 = stock, 1 = tool
            remainder = automationHandler.insertItem(slot, remainder, false);
        if (remainder.getCount() != stack.getCount())
            sendData();
        return remainder;
    }

    /** Can this item go into the lathe at all? */
    public boolean accepts(ItemStack stack) {
        return isValidStock(stack) || stack.is(ModItems.CARBIDE_INSERT.get());
    }

    /** Hand back finished parts. If none, the stock. If no stock either, the insert. */
    public void unloadInto(Player player) {
        boolean gaveSomething = false;
        for (int slot = 0; slot < outputInv.getSlots(); slot++) {
            ItemStack stack = outputInv.getStackInSlot(slot);
            if (stack.isEmpty())
                continue;
            gaveSomething = true;
            player.getInventory().placeItemBackInInventory(stack);
            outputInv.setStackInSlot(slot, ItemStack.EMPTY);
        }
        if (!gaveSomething && !inputInv.getStackInSlot(0).isEmpty()) {
            gaveSomething = true;
            player.getInventory().placeItemBackInInventory(inputInv.getStackInSlot(0));
            inputInv.setStackInSlot(0, ItemStack.EMPTY);
            timer = 0;
        }
        if (!gaveSomething) {
            player.getInventory().placeItemBackInInventory(toolInv.getStackInSlot(0));
            toolInv.setStackInSlot(0, ItemStack.EMPTY);
        }
        setChanged();
        sendData();
    }

    /** Is there a turning recipe that accepts this item as stock? */
    public boolean isValidStock(ItemStack stack) {
        ItemStackHandler tester = new ItemStackHandler(1);
        tester.setStackInSlot(0, stack);
        RecipeWrapper input = new RecipeWrapper(tester);
        if (lastRecipe != null && lastRecipe.matches(input, level))
            return true;
        return ModRecipeTypes.TURNING.find(input, level).isPresent();
    }

    // ---------------------------------------------------------------------------------------------
    // Engineer's Goggles readout: the operator's view of the setup
    // ---------------------------------------------------------------------------------------------

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        super.addToGoggleTooltip(tooltip, isPlayerSneaking); // Create's stress info

        tooltip.add(goggleLine(Component.translatable("create_machining.goggles.lathe_header")
                .withStyle(ChatFormatting.WHITE)));

        // Tool condition
        ItemStack tool = toolInv.getStackInSlot(0);
        if (tool.isEmpty()) {
            tooltip.add(goggleLine(Component.translatable("create_machining.goggles.no_tool")
                    .withStyle(ChatFormatting.RED)));
        } else {
            int lifePercent = Math.round(100f * (tool.getMaxDamage() - tool.getDamageValue()) / tool.getMaxDamage());
            tooltip.add(goggleLine(Component.translatable("create_machining.goggles.tool_life", lifePercent)
                    .withStyle(lifePercent > 25 ? ChatFormatting.GRAY : ChatFormatting.GOLD)));
        }

        // Spindle speed vs. the ideal band for whatever's loaded
        TurningRecipe recipe = inputInv.getStackInSlot(0).isEmpty() ? null : findRecipe();
        if (recipe != null) {
            int rpm = Math.round(Math.abs(getSpeed()));
            String key = rpm < recipe.getMinRpm() ? "too_slow" : rpm > recipe.getMaxRpm() ? "too_fast" : "in_band";
            tooltip.add(goggleLine(Component.translatable("create_machining.goggles.ideal_speed",
                    recipe.getMinRpm(), recipe.getMaxRpm()).withStyle(ChatFormatting.GRAY)));
            tooltip.add(goggleLine(Component.translatable("create_machining.goggles." + key, rpm)
                    .withStyle(key.equals("in_band") ? ChatFormatting.GREEN : ChatFormatting.GOLD)));
        }
        return true;
    }

    /** Create indents goggle lines by 4 spaces. */
    private static Component goggleLine(Component text) {
        return Component.literal("    ").append(text);
    }

    // ---------------------------------------------------------------------------------------------
    // Effects (client side only)
    // ---------------------------------------------------------------------------------------------

    @Override
    @OnlyIn(Dist.CLIENT)
    public void tickAudio() {
        super.tickAudio();
        if (getSpeed() == 0 || inputInv.getStackInSlot(0).isEmpty() || toolInv.getStackInSlot(0).isEmpty())
            return;
        // borrow the Millstone's grinding hum for now, pitched up with RPM
        float pitch = Mth.clamp((Math.abs(getSpeed()) / 256f) + .45f, .85f, 1f);
        SoundScapes.play(AmbienceGroup.MILLING, worldPosition, pitch);
    }

    /** Flings little bits of the stock off the cut, at spindle height. */
    private void spawnChipParticles() {
        ItemStack stock = inputInv.getStackInSlot(0);
        if (stock.isEmpty() || level.random.nextInt(3) != 0)
            return;
        ItemParticleOption chip = new ItemParticleOption(ParticleTypes.ITEM, stock);
        Vec3 center = Vec3.atCenterOf(worldPosition);
        double vx = (level.random.nextDouble() - .5) * .15;
        double vz = (level.random.nextDouble() - .5) * .15;
        level.addParticle(chip, center.x, center.y, center.z, vx, .1, vz);
    }

    // ---------------------------------------------------------------------------------------------
    // Saving and syncing
    // ---------------------------------------------------------------------------------------------

    /** Write our state to NBT (Minecraft's save format) for saving to disk and for syncing to clients. */
    @Override
    public void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        tag.putInt("Timer", timer);
        tag.putFloat("CycleSpeedFactor", cycleSpeedFactor);
        tag.put("InputInventory", inputInv.serializeNBT(registries));
        tag.put("ToolInventory", toolInv.serializeNBT(registries));
        tag.put("OutputInventory", outputInv.serializeNBT(registries));
        super.write(tag, registries, clientPacket);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        timer = tag.getInt("Timer");
        cycleSpeedFactor = tag.contains("CycleSpeedFactor") ? tag.getFloat("CycleSpeedFactor") : 1;
        inputInv.deserializeNBT(registries, tag.getCompound("InputInventory"));
        toolInv.deserializeNBT(registries, tag.getCompound("ToolInventory"));
        outputInv.deserializeNBT(registries, tag.getCompound("OutputInventory"));
        super.read(tag, registries, clientPacket);
    }

    /** Lathe was broken: drop what's inside instead of deleting it. */
    @Override
    public void destroy() {
        super.destroy();
        ItemHelper.dropContents(level, worldPosition, inputInv);
        ItemHelper.dropContents(level, worldPosition, toolInv);
        ItemHelper.dropContents(level, worldPosition, outputInv);
    }

    @Override
    public void invalidate() {
        super.invalidate();
        invalidateCapabilities();
    }

    /**
     * The inventories as automation sees them: stock goes into the input slot, inserts into the
     * tool slot, and only finished parts can be pulled out.
     *
     * This is an "inner class": a class defined inside another, which can use the outer object's
     * fields (inputInv, toolInv, outputInv) directly.
     */
    private class LatheInventoryHandler extends CombinedInvWrapper {
        LatheInventoryHandler() {
            super(inputInv, toolInv, outputInv); // slot 0 = stock, 1 = tool, 2-5 = parts
        }

        private IItemHandler handlerFor(int slot) {
            return getHandlerFromIndex(getIndexForSlot(slot));
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            IItemHandler handler = handlerFor(slot);
            if (handler == inputInv)
                return isValidStock(stack);
            if (handler == toolInv)
                return stack.is(ModItems.CARBIDE_INSERT.get());
            return false; // nothing goes into the output slots from outside
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (!isItemValid(slot, stack))
                return stack;
            return super.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (handlerFor(slot) != outputInv)
                return ItemStack.EMPTY;
            return super.extractItem(slot, amount, simulate);
        }
    }
}
