package com.jessehandke.createmachining.content.inspection;

import java.util.List;

import com.jessehandke.createmachining.content.quality.QualityModel;
import com.jessehandke.createmachining.registry.ModBlockEntities;
import com.jessehandke.createmachining.registry.ModDataComponents;
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.item.ItemHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.CombinedInvWrapper;

/**
 * Measures machined parts one at a time. Each part takes INSPECTION_TIME ticks, then comes out
 * with its gage reading: the hidden process score from the lathe plus a 0-5% luck roll.
 *
 * SmartBlockEntity is Create's base for block entities that tick and sync, minus the kinetics.
 */
public class InspectionStationBlockEntity extends SmartBlockEntity {
    /** Ticks per part. 20 ticks = 1 second. */
    public static final int INSPECTION_TIME = 40;

    public final ItemStackHandler inputInv = new ItemStackHandler(1);
    public final ItemStackHandler outputInv = new ItemStackHandler(9);
    private final IItemHandler automationHandler = new InspectionInventoryHandler();

    /** Ticks left on the part currently being measured. 0 = idle. */
    public int timer;

    public InspectionStationBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.INSPECTION_STATION.get(),
                (be, side) -> be.automationHandler);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        behaviours.add(new DirectBeltInputBehaviour(this));
    }

    /** Can this be inspected? Only machined parts that haven't been measured yet. */
    public static boolean isInspectable(ItemStack stack) {
        return stack.has(ModDataComponents.PROCESS_SCORE);
    }

    @Override
    public void tick() {
        super.tick();
        if (level.isClientSide)
            return;

        ItemStack part = inputInv.getStackInSlot(0);
        if (part.isEmpty()) {
            timer = 0;
            return;
        }

        if (timer == 0) {
            timer = INSPECTION_TIME; // start measuring
            sendData();
            return;
        }

        if (--timer > 0) // "--timer" subtracts 1 first, then compares (same as in C)
            return;

        ItemStack measured = measure(part);
        // Only finish if the result fits in the output; otherwise wait and try again.
        if (!ItemHandlerHelper.insertItemStacked(outputInv, measured, true).isEmpty()) {
            timer = 1;
            return;
        }
        ItemHandlerHelper.insertItemStacked(outputInv, measured, false);
        part.shrink(1);
        inputInv.setStackInSlot(0, part);

        level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.BLOCKS, 0.4f, 1.8f);
        sendData();
        setChanged();
    }

    /** One part off the stack, with its hidden score swapped for a gage reading. */
    private ItemStack measure(ItemStack part) {
        ItemStack single = part.copyWithCount(1);
        int processScore = single.getOrDefault(ModDataComponents.PROCESS_SCORE, 0);
        int luckRoll = level.random.nextInt(QualityModel.LUCK_MAX + 1); // 0..5 inclusive
        single.remove(ModDataComponents.PROCESS_SCORE);
        single.set(ModDataComponents.GAGE, QualityModel.gage(processScore, luckRoll));
        return single;
    }

    // ---------------------------------------------------------------------------------------------
    // Interaction helpers (used by InspectionStationBlock)
    // ---------------------------------------------------------------------------------------------

    public ItemStack insertPart(ItemStack stack) {
        ItemStack remainder = automationHandler.insertItem(0, stack.copy(), false);
        if (remainder.getCount() != stack.getCount())
            sendData();
        return remainder;
    }

    /** Hand back measured parts, or the unmeasured ones if nothing's done yet. */
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
        if (!gaveSomething) {
            player.getInventory().placeItemBackInInventory(inputInv.getStackInSlot(0));
            inputInv.setStackInSlot(0, ItemStack.EMPTY);
            timer = 0;
        }
        setChanged();
        sendData();
    }

    // ---------------------------------------------------------------------------------------------
    // Saving and syncing
    // ---------------------------------------------------------------------------------------------

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
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

    /** Uninspected parts in, measured parts out. */
    private class InspectionInventoryHandler extends CombinedInvWrapper {
        InspectionInventoryHandler() {
            super(inputInv, outputInv); // slot 0 = input, 1-9 = measured
        }

        private boolean isOutputSlot(int slot) {
            return getHandlerFromIndex(getIndexForSlot(slot)) == outputInv;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return !isOutputSlot(slot) && isInspectable(stack);
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
