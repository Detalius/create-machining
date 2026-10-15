package com.jessehandke.createmachining.client;

import com.jessehandke.createmachining.CreateMachining;
import com.jessehandke.createmachining.registry.ModBlockEntities;
import com.simibubi.create.content.kinetics.base.ShaftRenderer;
import com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual;

import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Client-only setup. dist = Dist.CLIENT means this class never loads on a dedicated server,
 * which matters because servers don't have any rendering code.
 */
@Mod(value = CreateMachining.MOD_ID, dist = Dist.CLIENT)
public class CreateMachiningClient {

    public CreateMachiningClient(IEventBus modEventBus) {
        modEventBus.addListener(CreateMachiningClient::registerRenderers);
        modEventBus.addListener(CreateMachiningClient::clientSetup);
    }

    /**
     * The spinning spindle/workpiece is drawn as a rotating Create shaft through the lathe.
     * Create has two render paths. This one is the fallback renderer...
     */
    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.CNC_LATHE.get(), ShaftRenderer::new);
    }

    /** ...and this is the fast one, through Flywheel (Create's instanced renderer). */
    private static void clientSetup(FMLClientSetupEvent event) {
        SimpleBlockEntityVisualizer.builder(ModBlockEntities.CNC_LATHE.get())
                .factory(SingleAxisRotatingVisual::shaft)
                .skipVanillaRender(be -> false)
                .apply();
    }
}
