package com.github.cerealklla.lyfe.cook;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CookingListenerTest {

    @Test
    void iconFormulaIsLiteral0_25PerComponentNoRoundingSurprise() {
        // Level-50 Cook, Tier 5 structure, 35 components -> 5 + 5 + 8.75 = 18.75 (the original
        // spec's own worked example said "18.5", confirmed a typo -- see decisions.md, 2026-10-03).
        double icons = CookingListener.computeIcons(5, 50, 35);
        assertEquals(18.75, icons, 1e-9);
    }

    @Test
    void levelContributionStepsEveryFiveLevels() {
        assertEquals(0.0, CookingListener.computeIcons(0, 0, 0), 1e-9);
        assertEquals(0.5, CookingListener.computeIcons(0, 5, 0), 1e-9);
        assertEquals(0.5, CookingListener.computeIcons(0, 9, 0), 1e-9);
        assertEquals(1.0, CookingListener.computeIcons(0, 10, 0), 1e-9);
    }

    @Test
    void xpForCraftScalesWithIcons() {
        long low = CookingListener.xpForCraft(1.0);
        long high = CookingListener.xpForCraft(18.75);
        assertEquals(true, high > low);
    }
}
