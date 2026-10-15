package com.jessehandke.createmachining.client;

import com.jessehandke.createmachining.CreateMachining;
import com.jessehandke.createmachining.content.quality.QualityGrade;
import com.jessehandke.createmachining.registry.ModBlockEntities;
import com.jessehandke.createmachining.registry.ModDataComponents;
import com.simibubi.create.content.kinetics.base.ShaftRenderer;
import com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual;

import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
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

    /** "Uninspected" on fresh parts, "Quality: Perfect" (etc.) on inspected ones. */
    private static void addQualityTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        QualityGrade grade = stack.get(ModDataComponents.GRADE); // null if the part has no grade
        if (grade != null) {
            event.getToolTip().add(1, Component.translatable("create_machining.tooltip.quality",
                    Component.translatable("create_machining.grade." + grade.id()).withStyle(gradeColor(grade)))
                    .withStyle(ChatFormatting.GRAY));
        } else if (stack.has(ModDataComponents.PROCESS_SCORE)) {
            event.getToolTip().add(1, Component.translatable("create_machining.tooltip.uninspected")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
    }

    /**
     * Java's "switch expression": like C's switch, but each case gives back a value and there's
     * no fall-through, so no break statements needed.
     */
    private static ChatFormatting gradeColor(QualityGrade grade) {
        return switch (grade) {
            case PERFECT -> ChatFormatting.AQUA;
            case IN_TOLERANCE -> ChatFormatting.GREEN;
            case OUT_OF_TOLERANCE -> ChatFormatting.YELLOW;
            case NEEDS_REWORK -> ChatFormatting.GOLD;
            case SCRAP -> ChatFormatting.RED;
        };
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
