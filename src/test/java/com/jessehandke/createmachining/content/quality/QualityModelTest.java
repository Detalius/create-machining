package com.jessehandke.createmachining.content.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the quality math. Run with: ./gradlew test
 * Each @Test method is one check; assertEquals(expected, actual, tolerance) fails the test if
 * the actual value is out of tolerance. Very much a first-article inspection for code.
 */
class QualityModelTest {
    private static final float EPS = 1e-4f;

    @Test
    void speedInsideBandIsPerfect() {
        assertEquals(1f, QualityModel.speedFactor(32, 32, 96), EPS);
        assertEquals(1f, QualityModel.speedFactor(64, 32, 96), EPS);
        assertEquals(1f, QualityModel.speedFactor(96, 32, 96), EPS);
        // Create uses negative RPM for the other direction; a lathe doesn't care
        assertEquals(1f, QualityModel.speedFactor(-64, 32, 96), EPS);
    }

    @Test
    void speedOutsideBandFallsOff() {
        assertEquals(0.7f, QualityModel.speedFactor(16, 32, 96), EPS);   // 50% under
        assertEquals(0.8f, QualityModel.speedFactor(128, 32, 96), 0.01f); // 33% over
        assertEquals(QualityModel.FACTOR_FLOOR, QualityModel.speedFactor(1000, 32, 96), EPS);
    }

    @Test
    void toolWearCurve() {
        assertEquals(1.0f, QualityModel.toolFactor(1.0f), EPS);  // fresh
        assertEquals(0.9f, QualityModel.toolFactor(0.25f), EPS); // end of steady wear
        assertEquals(0.5f, QualityModel.toolFactor(0.0f), EPS);  // about to break
        // steady zone is gentle, breakdown zone is steep
        float steadyDrop = QualityModel.toolFactor(1.0f) - QualityModel.toolFactor(0.75f);
        float breakdownDrop = QualityModel.toolFactor(0.25f) - QualityModel.toolFactor(0.0f);
        assertTrue(breakdownDrop > steadyDrop * 5);
    }

    @Test
    void perfectProcessGagesBetween95And100() {
        int score = QualityModel.processScore(1f, 1f);
        assertEquals(95, score);
        for (int luck = 0; luck <= QualityModel.LUCK_MAX; luck++) {
            int gage = QualityModel.gage(score, luck);
            assertTrue(gage >= 95 && gage <= 100, "gage out of range: " + gage);
        }
    }

    @Test
    void overspeedDoublesWear() {
        assertEquals(1, QualityModel.toolWearPerPart(96, 96));
        assertEquals(2, QualityModel.toolWearPerPart(97, 96));
    }
}
