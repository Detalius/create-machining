package com.jessehandke.createmachining.registry;

import com.jessehandke.createmachining.CreateMachining;
import com.mojang.serialization.Codec;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Data components are extra data attached to a single item stack, like the damage on a pickaxe
 * or the name on a renamed sword. We use two: one hidden, one shown.
 *
 * Two stacks only merge if their components match exactly, which gives us lot control for free:
 * parts from the same setup stack together, parts from a different setup don't.
 */
public final class ModDataComponents {
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(CreateMachining.MOD_ID);

    /** Hidden 0-95 score set by the lathe. Present = part hasn't been inspected yet. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> PROCESS_SCORE =
            COMPONENTS.registerComponentType("process_score", builder -> builder
                    .persistent(Codec.INT)                    // how it's saved to disk
                    .networkSynchronized(ByteBufCodecs.VAR_INT)); // how it's sent to clients

    /** 0-100% reading from the inspection station. Present = inspected. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> GAGE =
            COMPONENTS.registerComponentType("gage", builder -> builder
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    private ModDataComponents() {}
}
