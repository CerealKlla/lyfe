package com.github.cerealklla.lyfe.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.OptionalLong;

import org.junit.jupiter.api.Test;

class XpCurveTest {

    private static final XpCurve CURVE = new XpCurve(List.of(10L, 30L, 60L));

    @Test
    void rejectsNonIncreasingThresholds() {
        assertThrows(IllegalArgumentException.class, () -> new XpCurve(List.of(10L, 10L)));
        assertThrows(IllegalArgumentException.class, () -> new XpCurve(List.of(10L, 5L)));
    }

    @Test
    void levelZeroBelowFirstThreshold() {
        assertEquals(0, CURVE.levelForXp(0));
        assertEquals(0, CURVE.levelForXp(9));
    }

    @Test
    void reachingAThresholdExactlyCountsAsThatLevel() {
        assertEquals(1, CURVE.levelForXp(10));
        assertEquals(2, CURVE.levelForXp(30));
        assertEquals(3, CURVE.levelForXp(60));
    }

    @Test
    void xpBeyondMaxLevelStaysAtMaxLevel() {
        assertEquals(3, CURVE.levelForXp(1000));
        assertEquals(3, CURVE.maxLevel());
    }

    @Test
    void xpToNextLevelReflectsRemainingGap() {
        assertEquals(OptionalLong.of(10), CURVE.xpToNextLevel(0));
        assertEquals(OptionalLong.of(1), CURVE.xpToNextLevel(9));
        assertTrue(CURVE.xpToNextLevel(60).isEmpty());
    }
}
