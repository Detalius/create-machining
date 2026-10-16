package com.jessehandke.createmachining.content.cnc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * Turns machine-local positions (see CncLathePart) into world positions for a machine whose front
 * faces a given direction. All the "which way is left" math lives here and nowhere else.
 *
 * "Machine space" is measured in pixels (16 per block): L along the bed (0-48), H up (0-32),
 * D from front to back (0-32). Models, the renderer and particles all use it.
 */
public final class CncLatheLayout {
    private CncLatheLayout() {}

    /** Headstock -> tailstock. Standing at the front, that's your right-hand side. */
    public static Direction lengthDir(Direction front) {
        return front.getCounterClockWise();
    }

    /** Front -> back. */
    public static Direction depthDir(Direction front) {
        return front.getOpposite();
    }

    /** World position of local (0,0,0): the bottom front block of the headstock. */
    public static BlockPos origin(BlockPos controllerPos, Direction front) {
        return controllerPos.relative(front); // the controller sits one row behind the origin
    }

    public static BlockPos posOf(CncLathePart part, BlockPos origin, Direction front) {
        return origin.relative(lengthDir(front), part.l)
                .above(part.h)
                .relative(depthDir(front), part.d);
    }

    /** Where the controller is, given any part's position and that part's identity. */
    public static BlockPos controllerFrom(BlockPos partPos, CncLathePart part, Direction front) {
        BlockPos origin = partPos.relative(lengthDir(front), -part.l)
                .below(part.h)
                .relative(depthDir(front), -part.d);
        return posOf(CncLathePart.CONTROLLER, origin, front);
    }

    /** A point in machine space (pixels) as a world coordinate. */
    public static Vec3 worldPoint(BlockPos controllerPos, Direction front, double L, double H, double D) {
        BlockPos origin = origin(controllerPos, front);
        Vec3 len = Vec3.atLowerCornerOf(lengthDir(front).getNormal());
        Vec3 dep = Vec3.atLowerCornerOf(depthDir(front).getNormal());
        // measured from the center of the origin block's floor, so the facing rotation is symmetric
        return Vec3.atBottomCenterOf(origin)
                .add(len.scale(L / 16 - 0.5))
                .add(0, H / 16, 0)
                .add(dep.scale(D / 16 - 0.5));
    }

    /** Blockstate-style Y rotation (degrees clockwise from above) for a machine facing this way. */
    public static int yRotation(Direction front) {
        return switch (front) {
            case EAST -> 90;
            case SOUTH -> 180;
            case WEST -> 270;
            default -> 0; // NORTH: the models are authored facing north
        };
    }
}
