package com.jessehandke.createmachining.content.quality;

/**
 * All of the part-quality math, in one place, with no Minecraft code in it.
 * Keeping it "pure" means we can unit test it (see QualityModelTest) and tweak numbers safely.
 *
 * A part's final gage reading (0-100%) is made of two pieces:
 *  - the PROCESS SCORE (0-95): decided at the lathe by spindle speed and tool condition
 *  - the LUCK ROLL (0-5): rolled at the inspection station, pure chance
 * So a perfect process lands anywhere from 95% to 100%.
 */
public final class QualityModel {
    /** Best possible process score. The last 5% is always luck. */
    public static final int PROCESS_MAX = 95;
    /** The luck roll is a whole number from 0 to LUCK_MAX (inclusive). */
    public static final int LUCK_MAX = 5;

    /** Worst a single factor can drag quality down to. Even awful setups make *something*. */
    public static final float FACTOR_FLOOR = 0.2f;
    /** How hard quality falls off outside the ideal RPM band (per 100% of deviation). */
    public static final float SPEED_PENALTY = 0.6f;

    /** Below this much remaining tool life, wear stops being gradual and starts hurting fast. */
    public static final float TOOL_BREAKDOWN_LIFE = 0.25f;
    /** Quality factor of a tool right at the start of breakdown (end of its "steady" wear). */
    public static final float TOOL_STEADY_END = 0.9f;
    /** Quality factor of a tool that's about to break. */
    public static final float TOOL_WORN_OUT = 0.5f;

    private QualityModel() {}

    /**
     * Feeds and speeds. 1.0 anywhere inside the ideal band, then drops linearly the further
     * outside you go, relative to the band edge. E.g. a 32-96 RPM band at 16 RPM is 50% under
     * the edge, so 1 - 0.6 * 0.5 = 0.7.
     */
    public static float speedFactor(float rpm, int minRpm, int maxRpm) {
        rpm = Math.abs(rpm);
        float deviation = 0;
        if (rpm < minRpm)
            deviation = (minRpm - rpm) / minRpm;
        else if (rpm > maxRpm)
            deviation = (rpm - maxRpm) / maxRpm;
        return Math.max(FACTOR_FLOOR, 1 - SPEED_PENALTY * deviation);
    }

    /**
     * Tool wear, shaped like a real insert's wear curve: a long steady stretch where quality
     * barely moves (1.0 down to 0.9), then a breakdown zone in the last 25% of life where it
     * drops quickly (0.9 down to 0.5).
     *
     * @param lifeRemaining 1.0 for a fresh insert, 0.0 for one on its last part
     */
    public static float toolFactor(float lifeRemaining) {
        float life = Math.clamp(lifeRemaining, 0f, 1f);
        if (life >= TOOL_BREAKDOWN_LIFE) {
            float wornFraction = (1 - life) / (1 - TOOL_BREAKDOWN_LIFE); // 0 fresh -> 1 at breakdown
            return 1 - (1 - TOOL_STEADY_END) * wornFraction;
        }
        float breakdownFraction = (TOOL_BREAKDOWN_LIFE - life) / TOOL_BREAKDOWN_LIFE; // 0 -> 1
        return TOOL_STEADY_END - (TOOL_STEADY_END - TOOL_WORN_OUT) * breakdownFraction;
    }

    /** The hidden score stamped on a part when it leaves the lathe. */
    public static int processScore(float speedFactor, float toolFactor) {
        return Math.round(PROCESS_MAX * speedFactor * toolFactor);
    }

    /**
     * The reading the inspection station reports.
     *
     * @param luckRoll a random whole number from 0 to LUCK_MAX
     */
    public static int gage(int processScore, int luckRoll) {
        return Math.clamp(processScore + luckRoll, 0, 100);
    }

    /** Running over the band cooks the insert: it wears twice as fast. */
    public static int toolWearPerPart(float rpm, int maxRpm) {
        return Math.abs(rpm) > maxRpm ? 2 : 1;
    }
}
