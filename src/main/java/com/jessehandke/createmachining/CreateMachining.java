package com.jessehandke.createmachining;

import org.slf4j.Logger;

import com.jessehandke.createmachining.registry.ModBlockEntities;
import com.jessehandke.createmachining.registry.ModBlocks;
import com.jessehandke.createmachining.registry.ModCreativeTabs;
import com.jessehandke.createmachining.registry.ModDataComponents;
import com.jessehandke.createmachining.registry.ModItems;
import com.jessehandke.createmachining.registry.ModRecipeTypes;
import com.jessehandke.createmachining.content.inspection.InspectionStationBlockEntity;
import com.jessehandke.createmachining.content.lathe.CncLatheBlockEntity;
import com.mojang.logging.LogUtils;
import com.simibubi.create.api.stress.BlockStressValues;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * Entry point of the mod. NeoForge finds this class through the @Mod annotation
 * (the string must match mod_id in gradle.properties) and calls the constructor once at startup.
 */
@Mod(CreateMachining.MOD_ID)
public class CreateMachining {
    public static final String MOD_ID = "create_machining";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CreateMachining(IEventBus modEventBus) {
        // Hand each of our registries to NeoForge. Nothing is actually created yet,
        // NeoForge calls our suppliers later, at the moment the game is ready for them.
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModDataComponents.COMPONENTS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITY_TYPES.register(modEventBus);
        ModRecipeTypes.register(modEventBus);
        ModCreativeTabs.TABS.register(modEventBus);

        // Method references (Class::method) work like function pointers in C:
        // "call this method when that event fires".
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(CncLatheBlockEntity::registerCapabilities);
        modEventBus.addListener(InspectionStationBlockEntity::registerCapabilities);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // How much stress (SU per RPM) the lathe puts on a Create network. The Millstone uses 4.0.
        BlockStressValues.IMPACTS.register(ModBlocks.CNC_LATHE.get(), () -> 4.0);
    }

    /** Shorthand for "create_machining:<path>" IDs. */
    public static ResourceLocation asResource(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
