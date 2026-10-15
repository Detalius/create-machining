package com.jessehandke.createmachining.registry;

import com.jessehandke.createmachining.CreateMachining;
import com.jessehandke.createmachining.content.inspection.InspectionStationBlock;
import com.jessehandke.createmachining.content.lathe.CncLatheBlock;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Every block the mod adds. A DeferredRegister is a to-do list: we write down
 * "make a block called X like this", and NeoForge runs it when registration happens.
 */
public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CreateMachining.MOD_ID);

    public static final DeferredBlock<CncLatheBlock> CNC_LATHE = BLOCKS.register("cnc_lathe",
            () -> new CncLatheBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0f, 6.0f)
                    .sound(SoundType.NETHERITE_BLOCK)
                    .requiresCorrectToolForDrops()
                    // the model isn't a full cube, so don't let it hide its neighbours' faces
                    .noOcclusion()));

    public static final DeferredBlock<InspectionStationBlock> INSPECTION_STATION = BLOCKS.register("inspection_station",
            () -> new InspectionStationBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(2.5f, 6.0f)
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    private ModBlocks() {}
}
