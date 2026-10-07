package com.github.cerealklla.lyfe.xpbar;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.github.cerealklla.lyfe.skill.XpCurve;

class XpBarProgressTest {

    // Level 1 at 10 xp, level 2 at 30 xp, level 3 (max) at 60 xp.
    private static final XpCurve CURVE = new XpCurve(List.of(10L, 30L, 60L));

    @Test
    void sameLevelPartialFill() {
        // Still level 1 before and after (10..30 span): 15 -> 20 is 25% -> 50% through that span.
        XpBarProgress progress = XpBarProgress.compute(CURVE, 15, 20);
        assertEquals(1, progress.level());
        assertEquals(0.25, progress.startFraction(), 1e-9);
        assertEquals(0.5, progress.endFraction(), 1e-9);
    }

    @Test
    void levelUpResetsStartToZero() {
        // 25 is level 1 (mid-span); 35 is level 2 -- the bar should reset to empty, not carry over
        // a fraction computed against the wrong level's span.
        XpBarProgress progress = XpBarProgress.compute(CURVE, 25, 35);
        assertEquals(2, progress.level());
        assertEquals(0.0, progress.startFraction(), 1e-9);
        // Level 2 span is 30..60; 35 is 1/6 of the way through it.
        assertEquals(5.0 / 30.0, progress.endFraction(), 1e-9);
    }

    @Test
    void multiLevelJumpInOneGainAlsoResetsToZero() {
        // A huge gain skipping straight from level 0 to level 3 (max).
        XpBarProgress progress = XpBarProgress.compute(CURVE, 0, 60);
        assertEquals(3, progress.level());
        assertEquals(0.0, progress.startFraction(), 1e-9);
        assertEquals(1.0, progress.endFraction(), 1e-9);
    }

    @Test
    void maxLevelBothBeforeAndAfterShowsFullStaticBar() {
        XpBarProgress progress = XpBarProgress.compute(CURVE, 60, 90);
        assertEquals(3, progress.level());
        assertEquals(1.0, progress.startFraction(), 1e-9);
        assertEquals(1.0, progress.endFraction(), 1e-9);
    }

    @Test
    void levelZeroPartialFill() {
        XpBarProgress progress = XpBarProgress.compute(CURVE, 0, 5);
        assertEquals(0, progress.level());
        assertEquals(0.0, progress.startFraction(), 1e-9);
        assertEquals(0.5, progress.endFraction(), 1e-9);
    }
}
