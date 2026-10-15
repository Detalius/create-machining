package com.jessehandke.createmachining.client;

import com.jessehandke.createmachining.CreateMachining;
import com.jessehandke.createmachining.registry.ModBlockEntities;
import com.jessehandke.createmachining.registry.ModDataComponents;
import com.simibubi.create.content.kinetics.base.ShaftRenderer;
import com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual;

import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Client-only setup. dist = Dist.CLIENT means this class never loads on a dedicated server,
 * which matters because servers don't have any rendering code.
 */
@Mod(value = CreateMachining.MOD_ID, dist = Dist.CLIENT)
public class CreateMachiningClient {

    public CreateMachiningClient(IEventBus modEventBus) {
        modEventBus.addListener(CreateMachiningClient::registerRenderers);
        modEventBus.addListener(CreateMachiningClient::clientSetup);
        // Tooltips fire on the game event bus (NeoForge.EVENT_BUS), not the mod's startup bus
        NeoForge.EVENT_BUS.addListener(CreateMachiningClient::addQualityTooltip);
    }

    /** "Uninspected" on fresh parts, "Gage: 97%" on measured ones. */
    private static void addQualityTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        Integer gage = stack.get(ModDataComponents.GAGE); // Integer (not int) because it can be null
        if (gage != null) {
            event.getToolTip().add(1, Component.translatable("create_machining.tooltip.gage",
                    Component.literal(gage + "%").withColor(gageColor(gage))).withStyle(ChatFormatting.GRAY));
        } else if (stack.has(ModDataComponents.PROCESS_SCORE)) {
            event.getToolTip().add(1, Component.translatable("create_machining.tooltip.uninspected")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
    }

    /**
     * Smooth red to green, no hard bands: 50% and below is red, 100% is green, everything in
     * between slides through orange and yellow. (Hue 0 = red, 1/3 = green, on a 0-1 color wheel.)
     */
    private static int gageColor(int gage) {
        float t = Mth.clamp((gage - 50) / 50f, 0f, 1f);
        return Mth.hsvToRgb(t / 3f, 0.75f, 1f);
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
