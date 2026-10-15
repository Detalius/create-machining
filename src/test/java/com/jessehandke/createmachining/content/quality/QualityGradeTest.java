package com.jessehandke.createmachining.content.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Every band edge, both sides. */
class QualityGradeTest {

    @Test
    void bandEdges() {
        assertEquals(QualityGrade.PERFECT, QualityGrade.fromGage(100));
        assertEquals(QualityGrade.PERFECT, QualityGrade.fromGage(95));
        assertEquals(QualityGrade.IN_TOLERANCE, QualityGrade.fromGage(94));
        assertEquals(QualityGrade.IN_TOLERANCE, QualityGrade.fromGage(85));
        assertEquals(QualityGrade.OUT_OF_TOLERANCE, QualityGrade.fromGage(84));
        assertEquals(QualityGrade.OUT_OF_TOLERANCE, QualityGrade.fromGage(75));
        assertEquals(QualityGrade.NEEDS_REWORK, QualityGrade.fromGage(74));
        assertEquals(QualityGrade.NEEDS_REWORK, QualityGrade.fromGage(60));
        assertEquals(QualityGrade.SCRAP, QualityGrade.fromGage(59));
        assertEquals(QualityGrade.SCRAP, QualityGrade.fromGage(0));
    }

    @Test
    void perfectSetupAlwaysGradesPerfect() {
        int score = QualityModel.processScore(1f, 1f);
        for (int luck = 0; luck <= QualityModel.LUCK_MAX; luck++)
            assertEquals(QualityGrade.PERFECT, QualityGrade.fromGage(QualityModel.gage(score, luck)));
    }

    @Test
    void idsMatchJson() {
        assertEquals("perfect", QualityGrade.PERFECT.id());
        assertEquals("out_of_tolerance", QualityGrade.OUT_OF_TOLERANCE.id());
    }
}
