package com.jessehandke.createmachining.content.cnc;

import net.minecraft.util.Mth;

/**
 * Where the moving parts are, in machine space (pixels: L along the bed, H up, D front-to-back).
 * Shared by the renderer (to draw them) and the block entity (to aim particles), so the chips and
 * coolant always come from where the tool visibly is.
 *
 * The tool path for one cycle, like a simple OD turning program:
 *   0-10%   plunge:   tool moves in from the back to the stock surface, near the tailstock end
 *   10-90%  traverse: tool feeds along the stock toward the chuck
 *   90-100% retract:  tool backs off
 * Then the turret returns to its home position while the door opens.
 */
public final class CncLatheGeometry {
    private CncLatheGeometry() {}

    /** The spindle centerline. */
    public static final float SPINDLE_H = 24, SPINDLE_D = 8;
    /** Stock radius, so the tool tip touches the surface at SPINDLE_D + STOCK_RADIUS. */
    public static final float STOCK_RADIUS = 3;
    /** Chuck face and tailstock quill tip: the stock spans between them. */
    public static final float CHUCK_FACE_L = 18, QUILL_TIP_L = 34;

    /** Tool tip, start and end of the cut along the bed. */
    public static final float CUT_START_L = 32, CUT_END_L = 20;
    /** Turret home: tool tip parked toward the back, level with the cut start. */
    public static final float HOME_L = CUT_START_L, HOME_D = 18;
    public static final float CUT_D = SPINDLE_D + STOCK_RADIUS;

    private static final float PLUNGE_END = 0.1f, TRAVERSE_END = 0.9f;

    /** Tool tip L during the cut. */
    public static float cutPositionL(float progress) {
        if (progress <= PLUNGE_END)
            return CUT_START_L;
        if (progress >= TRAVERSE_END)
            return CUT_END_L;
        float t = (progress - PLUNGE_END) / (TRAVERSE_END - PLUNGE_END);
        return Mth.lerp(t, CUT_START_L, CUT_END_L);
    }

    /** Tool tip D during the cut. */
    public static float cutPositionD(float progress) {
        if (progress <= PLUNGE_END)
            return Mth.lerp(smooth(progress / PLUNGE_END), HOME_D, CUT_D);
        if (progress >= TRAVERSE_END)
            return Mth.lerp(smooth((progress - TRAVERSE_END) / (1 - TRAVERSE_END)), CUT_D, HOME_D);
        return CUT_D;
    }

    /**
     * Tool tip position {L, D} for any phase. doorT is the door animation's 0..1 progress, which
     * doubles as the turret's "return home" progress while the door opens.
     */
    public static float[] toolTip(CncLatheBlockEntity.Phase phase, float cutProgress, float doorT) {
        return switch (phase) {
            case CUTTING -> new float[] { cutPositionL(cutProgress), cutPositionD(cutProgress) };
            case OPENING -> new float[] { Mth.lerp(smooth(doorT), CUT_END_L, HOME_L), HOME_D };
            default -> new float[] { HOME_L, HOME_D };
        };
    }

    /** Ease in and out, so moves start and stop gently instead of snapping. */
    public static float smooth(float t) {
        t = Mth.clamp(t, 0, 1);
        return t * t * (3 - 2 * t);
    }
}
