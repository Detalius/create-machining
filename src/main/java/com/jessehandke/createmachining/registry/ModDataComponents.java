package com.jessehandke.createmachining.registry;

import com.jessehandke.createmachining.CreateMachining;
import com.jessehandke.createmachining.content.quality.QualityGrade;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Data components are extra data attached to a single item stack, like the damage on a pickaxe
 * or the name on a renamed sword.
 *
 * Two stacks only merge if their components match exactly. That's why inspected parts carry just
 * a grade (5 possible values) rather than a raw percentage: at most 5 kinds of stack per part.
 */
public final class ModDataComponents {
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, CreateMachining.MOD_ID);

    /**
     * Hidden 0-95 score set by the lathe. Present = part hasn't been inspected yet.
     * The lathe averages it across a lot so uninspected parts stack in its output.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> PROCESS_SCORE =
            COMPONENTS.registerComponentType("process_score", builder -> builder
                    .persistent(Codec.INT)                         // how it's saved to disk
                    .networkSynchronized(ByteBufCodecs.VAR_INT));  // how it's sent to clients

    /**
     * Reads/writes a grade as its id() string, e.g. "perfect". A typo in a recipe file becomes a
     * readable load error ("Unknown quality grade: perfct") instead of a crash.
     */
    private static final Codec<QualityGrade> GRADE_CODEC = Codec.STRING.comapFlatMap(
            id -> {
                for (QualityGrade grade : QualityGrade.values())
                    if (grade.id().equals(id))
                        return DataResult.success(grade);
                return DataResult.error(() -> "Unknown quality grade: " + id);
            },
            QualityGrade::id);

    /**
     * Grade from the inspection station. Present = inspected.
     * In JSON recipes this reads as e.g. "create_machining:grade": "perfect".
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<QualityGrade>> GRADE =
            COMPONENTS.registerComponentType("grade", builder -> builder
                    // a Codec that reads/writes the grade as its id() string, e.g. "perfect"
                    .persistent(GRADE_CODEC)
                    .networkSynchronized(NeoForgeStreamCodecs.enumCodec(QualityGrade.class)));

    private ModDataComponents() {}
}
