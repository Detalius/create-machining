package com.jessehandke.createmachining.content.lathe;

import com.jessehandke.createmachining.content.quality.QualityModel;
import com.jessehandke.createmachining.registry.ModDataComponents;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Lathe rules shared by the Bench Lathe and the CNC Lathe, so both machines behave identically.
 * Everything here is "static": plain functions that don't belong to any one machine, like free
 * functions in C.
 */
public final class LatheLogic {
    private LatheLogic() {}

    /** Hidden process score for a part finished with this tool after this cycle's worst speed factor. */
    public static int processScore(float cycleSpeedFactor, ItemStack tool) {
        float toolLife = 1 - (float) tool.getDamageValue() / tool.getMaxDamage();
        return QualityModel.processScore(cycleSpeedFactor, QualityModel.toolFactor(toolLife));
    }

    /**
     * Put fresh parts into a parts bin as one lot. If the bin already holds uninspected parts of
     * the same kind, they're pooled into a single stack with a count-weighted average score
     * (like mixing parts in one tote), so they don't split into a stack per score.
     */
    public static void insertIntoLot(ItemStackHandler bin, ItemStack parts, int processScore) {
        for (int slot = 0; slot < bin.getSlots() && !parts.isEmpty(); slot++) {
            ItemStack lot = bin.getStackInSlot(slot);
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
            bin.setStackInSlot(slot, merged);
            parts.shrink(moved);
        }
        if (!parts.isEmpty()) {
            parts.set(ModDataComponents.PROCESS_SCORE, processScore);
            ItemHandlerHelper.insertItemStacked(bin, parts, false);
        }
    }

    /** Wear the insert in slot 0 of the tool holder. A worn-out insert breaks and is removed. */
    public static void wearTool(ItemStackHandler toolHolder, int amount, Level level, BlockPos pos) {
        ItemStack tool = toolHolder.getStackInSlot(0);
        int newDamage = tool.getDamageValue() + amount;
        if (newDamage >= tool.getMaxDamage()) {
            toolHolder.setStackInSlot(0, ItemStack.EMPTY);
            level.playSound(null, pos, SoundEvents.ITEM_BREAK, SoundSource.BLOCKS, 1, 1);
            return;
        }
        tool.setDamageValue(newDamage);
        toolHolder.setStackInSlot(0, tool);
    }

    /** True if every slot is full to its limit (the machine should hold until it's emptied). */
    public static boolean isFull(ItemStackHandler bin) {
        for (int i = 0; i < bin.getSlots(); i++)
            if (bin.getStackInSlot(i).getCount() < bin.getSlotLimit(i))
                return false;
        return true;
    }

    /** True if any slot is full (the Bench Lathe's simpler "bin full" rule). */
    public static boolean anySlotFull(ItemStackHandler bin) {
        for (int i = 0; i < bin.getSlots(); i++)
            if (bin.getStackInSlot(i).getCount() == bin.getSlotLimit(i))
                return true;
        return false;
    }
}
