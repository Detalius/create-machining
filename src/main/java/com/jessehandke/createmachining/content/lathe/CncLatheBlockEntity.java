package com.jessehandke.createmachining.content.lathe;

import java.util.List;
import java.util.Optional;

import com.jessehandke.createmachining.registry.ModBlockEntities;
import com.jessehandke.createmachining.registry.ModRecipeTypes;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.item.ItemHelper;
import com.simibubi.create.foundation.sound.SoundScapes;
import com.simibubi.create.foundation.sound.SoundScapes.AmbienceGroup;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
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
 * One placed lathe: its inventory, its cycle timer, and the logic that runs every game tick
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
    public final ItemStackHandler outputInv = new ItemStackHandler(4);
    // The view of our inventory that hoppers, funnels and belts get to see.
    private final IItemHandler automationHandler = new LatheInventoryHandler();

    /** Remaining cycle time. Counts down faster at higher RPM; the part is done when it hits 0. */
    public int timer;
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

        // Parts bin full? Feed hold until someone empties it.
        for (int i = 0; i < outputInv.getSlots(); i++)
            if (outputInv.getStackInSlot(i).getCount() == outputInv.getSlotLimit(i))
                return;

        // Mid-cycle: count down
        if (timer > 0) {
            timer -= getProcessingSpeed();
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

        RecipeWrapper input = new RecipeWrapper(inputInv);
        if (lastRecipe == null || !lastRecipe.matches(input, level)) {
            Optional<RecipeHolder<TurningRecipe>> recipe = ModRecipeTypes.TURNING.find(input, level);
            if (recipe.isEmpty()) {
                timer = IDLE_RECHECK;
                sendData();
                return;
            }
            lastRecipe = recipe.get().value();
        }

        // Cycle start
        timer = lastRecipe.getProcessingDuration();
        sendData();
    }

    /**
     * Higher RPM means a faster cycle. Same formula as the Millstone for now: 1 at low speed,
     * up to 16 per tick at Create's max of 256 RPM. Proper feeds-and-speeds comes later.
     */
    public int getProcessingSpeed() {
        return Mth.clamp((int) Math.abs(getSpeed() / 16f), 1, 512);
    }

    private void finishPart() {
        RecipeWrapper input = new RecipeWrapper(inputInv);
        if (lastRecipe == null || !lastRecipe.matches(input, level)) {
            Optional<RecipeHolder<TurningRecipe>> recipe = ModRecipeTypes.TURNING.find(input, level);
            if (recipe.isEmpty())
                return;
            lastRecipe = recipe.get().value();
        }

        // Use up one piece of stock
        ItemStack stock = inputInv.getStackInSlot(0);
        stock.shrink(1);
        inputInv.setStackInSlot(0, stock);

        // rollResults rolls the dice for chance outputs (like swarf) and returns what came out
        for (ItemStack result : lastRecipe.rollResults(level.random))
            ItemHandlerHelper.insertItemStacked(outputInv, result, false);

        sendData();  // sync to clients
        setChanged(); // mark for saving
    }

    // ---------------------------------------------------------------------------------------------
    // Interaction helpers (used by CncLatheBlock when a player right-clicks)
    // ---------------------------------------------------------------------------------------------

    /** Try to load stock. Returns whatever didn't fit, or the whole stack if it's not lathe stock. */
    public ItemStack insertStock(ItemStack stack) {
        ItemStack remainder = automationHandler.insertItem(0, stack.copy(), false);
        if (remainder.getCount() != stack.getCount())
            sendData();
        return remainder;
    }

    /** Hand finished parts to the player. If there are none, hand back the stock instead. */
    public void unloadInto(Player player) {
        boolean hadParts = false;
        for (int slot = 0; slot < outputInv.getSlots(); slot++) {
            ItemStack stack = outputInv.getStackInSlot(slot);
            if (stack.isEmpty())
                continue;
            hadParts = true;
            player.getInventory().placeItemBackInInventory(stack);
            outputInv.setStackInSlot(slot, ItemStack.EMPTY);
        }
        if (!hadParts) {
            player.getInventory().placeItemBackInInventory(inputInv.getStackInSlot(0));
            inputInv.setStackInSlot(0, ItemStack.EMPTY);
            timer = 0;
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
    // Effects (client side only)
    // ---------------------------------------------------------------------------------------------

    @Override
    @OnlyIn(Dist.CLIENT)
    public void tickAudio() {
        super.tickAudio();
        if (getSpeed() == 0 || inputInv.getStackInSlot(0).isEmpty())
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
        tag.put("InputInventory", inputInv.serializeNBT(registries));
        tag.put("OutputInventory", outputInv.serializeNBT(registries));
        super.write(tag, registries, clientPacket);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        timer = tag.getInt("Timer");
        inputInv.deserializeNBT(registries, tag.getCompound("InputInventory"));
        outputInv.deserializeNBT(registries, tag.getCompound("OutputInventory"));
        super.read(tag, registries, clientPacket);
    }

    /** Lathe was broken: drop what's inside instead of deleting it. */
    @Override
    public void destroy() {
        super.destroy();
        ItemHelper.dropContents(level, worldPosition, inputInv);
        ItemHelper.dropContents(level, worldPosition, outputInv);
    }

    @Override
    public void invalidate() {
        super.invalidate();
        invalidateCapabilities();
    }

    /**
     * The inventory as automation sees it: stock can only go IN to the input slot (and only if
     * it's valid stock), finished parts can only come OUT of the output slots.
     *
     * This is an "inner class": a class defined inside another, which can use the outer object's
     * fields (inputInv, outputInv) directly.
     */
    private class LatheInventoryHandler extends CombinedInvWrapper {
        LatheInventoryHandler() {
            super(inputInv, outputInv); // slot 0 = input, slots 1-4 = output
        }

        private boolean isOutputSlot(int slot) {
            return getHandlerFromIndex(getIndexForSlot(slot)) == outputInv;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return !isOutputSlot(slot) && isValidStock(stack) && super.isItemValid(slot, stack);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (!isItemValid(slot, stack))
                return stack;
            return super.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (!isOutputSlot(slot))
                return ItemStack.EMPTY;
            return super.extractItem(slot, amount, simulate);
        }
    }
}
