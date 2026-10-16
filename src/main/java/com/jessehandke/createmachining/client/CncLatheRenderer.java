package com.jessehandke.createmachining.client;

import com.jessehandke.createmachining.CreateMachining;
import com.jessehandke.createmachining.content.cnc.CncLatheBlockEntity;
import com.jessehandke.createmachining.content.cnc.CncLatheGeometry;
import com.jessehandke.createmachining.content.cnc.CncLatheLayout;
import com.jessehandke.createmachining.content.cnc.CncLathePart;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Draws the CNC Lathe's moving parts every frame: drive shaft, spinning chuck and stock, the
 * turret following its tool path, the sliding door, and the chip conveyor once it's installed.
 * The static shell is ordinary block models (see tools/gen_cnc_lathe_models.py).
 *
 * A PoseStack is a stack of transforms (like OpenGL's old matrix stack): push, translate/rotate,
 * draw, pop. Everything inside a push/pop pair is moved together.
 */
public class CncLatheRenderer extends SafeBlockEntityRenderer<CncLatheBlockEntity> {

    // "Partial models" are block models that aren't tied to a block, for drawing moving pieces.
    // They must be declared at startup, which is why init() is called from the client mod class.
    public static final PartialModel CHUCK = partial("chuck");
    public static final PartialModel WORKPIECE_IRON = partial("workpiece_iron");
    public static final PartialModel WORKPIECE_BRASS = partial("workpiece_brass");
    public static final PartialModel TURRET = partial("turret");
    public static final PartialModel DOOR = partial("door");
    public static final PartialModel CONVEYOR = partial("conveyor");

    private static PartialModel partial(String name) {
        return PartialModel.of(CreateMachining.asResource("block/cnc_lathe/moving/" + name));
    }

    /** Touching this class runs the static initializers above, registering the partials. */
    public static void init() {}

    /** How far the door slides when it opens, in pixels. */
    private static final float DOOR_TRAVEL = 16;

    public CncLatheRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    protected void renderSafe(CncLatheBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer,
                              int light, int overlay) {
        BlockState state = be.getBlockState();
        Direction front = be.front();

        // Drive shaft stub sticking out of the back, turning with the network (Create's own helper)
        KineticBlockEntityRenderer.standardKineticRotationTransform(
                CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, state, front.getOpposite()), be, light)
                .renderInto(ms, buffer.getBuffer(RenderType.solid()));

        // Light inside the enclosure (the controller sits in the base, which is darker)
        BlockPos workZone = CncLatheLayout.posOf(CncLathePart.WORK_ZONE, CncLatheLayout.origin(be.getBlockPos(), front), front);
        int workLight = LevelRenderer.getLightColor(be.getLevel(), workZone);

        ms.pushPose();
        // Rotate everything so we can work in the north-facing frame the models were authored in
        ms.translate(0.5, 0, 0.5);
        ms.mulPose(Axis.YP.rotationDegrees(-CncLatheLayout.yRotation(front)));
        ms.translate(-0.5, 0, -0.5);

        // Spindle: chuck and stock spin with the drive
        float spindleAngle = KineticBlockEntityRenderer.getAngleForBe(be, be.getBlockPos(), front.getAxis());
        renderAt(ms, buffer, state, CHUCK, workLight, 16, CncLatheGeometry.SPINDLE_H, CncLatheGeometry.SPINDLE_D,
                8, 8, 8, spindleAngle);
        ItemStack stock = be.stockInv.getStackInSlot(0);
        if (!stock.isEmpty()) {
            PartialModel bar = stock.getDescriptionId().contains("brass") ? WORKPIECE_BRASS : WORKPIECE_IRON;
            renderAt(ms, buffer, state, bar, workLight,
                    (CncLatheGeometry.CHUCK_FACE_L + CncLatheGeometry.QUILL_TIP_L) / 2,
                    CncLatheGeometry.SPINDLE_H, CncLatheGeometry.SPINDLE_D, 8, 8, 8, spindleAngle);
        }

        // Turret: tool tip follows the programmed path
        float doorOpen = be.doorOpenness(partialTicks);
        float[] tip = CncLatheGeometry.toolTip(be.phase, be.cutProgress(partialTicks), doorOpen);
        renderAt(ms, buffer, state, TURRET, workLight, tip[0], CncLatheGeometry.SPINDLE_H, tip[1], 8, 8, 2, 0);

        // Door: slides toward the tailstock to open
        float doorL = 23 + DOOR_TRAVEL * CncLatheGeometry.smooth(doorOpen);
        renderAt(ms, buffer, state, DOOR, workLight, doorL, 22, -0.1f, 8, 8, 0.5f, 0);

        // Chip conveyor, if installed
        if (be.conveyorInstalled)
            renderAt(ms, buffer, state, CONVEYOR, light, 30, 10, 21.5f, 8, 0, 7.5f, 0);

        ms.popPose();
    }

    /**
     * Draw a partial with its pivot (model pixels px,py,pz) placed at machine point L,H,D,
     * optionally spun about the spindle axis.
     */
    private static void renderAt(PoseStack ms, MultiBufferSource buffer, BlockState state, PartialModel model, int light,
                                 float L, float H, float D, float px, float py, float pz, float spin) {
        ms.pushPose();
        // machine space -> the controller's north-facing frame (the controller is cell l=0, d=1)
        ms.translate((16 - L) / 16f, H / 16f, (D - 16) / 16f);
        if (spin != 0)
            ms.mulPose(Axis.XP.rotation(spin));
        ms.translate(-px / 16f, -py / 16f, -pz / 16f);
        // Ask for the buffer right here: getting a different buffer (e.g. solid for the shaft)
        // closes the previous one, so a consumer fetched earlier could already be finished.
        CachedBuffers.partial(model, state).light(light).renderInto(ms, buffer.getBuffer(RenderType.cutout()));
        ms.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(CncLatheBlockEntity be) {
        return true; // the machine is bigger than its controller block
    }
}
