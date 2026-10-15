package com.jessehandke.createmachining.registry;

import com.jessehandke.createmachining.CreateMachining;
import com.jessehandke.createmachining.content.lathe.CncLatheBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * A "block entity type" is the factory for block entities: it knows how to build the
 * per-block object (with inventory, timers, etc.) and which blocks are allowed to have one.
 */
public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CreateMachining.MOD_ID);

    @SuppressWarnings("DataFlowIssue") // build(null) is normal here, the argument is only used by vanilla data fixers
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CncLatheBlockEntity>> CNC_LATHE =
            BLOCK_ENTITY_TYPES.register("cnc_lathe", () -> BlockEntityType.Builder
                    .of((pos, state) -> new CncLatheBlockEntity(ModBlockEntities.CNC_LATHE.get(), pos, state),
                            ModBlocks.CNC_LATHE.get())
                    .build(null));

    private ModBlockEntities() {}
}
