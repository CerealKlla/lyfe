package com.github.cerealklla.lyfe.durability;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DurabilityStatusTest {

    private static final int MAX = 100;

    @Test
    void freshItemIsGrey() {
        assertEquals(DurabilityStatus.Tier.GREY, DurabilityStatus.tierFor(0, MAX));
    }

    @Test
    void justAboveGreyThresholdIsGrey() {
        // 26% remaining -> damage 74
        assertEquals(DurabilityStatus.Tier.GREY, DurabilityStatus.tierFor(74, MAX));
    }

    @Test
    void exactlyTwentyFivePercentIsYellow() {
        // Threshold is exclusive on the grey side: >25 is grey, so exactly 25% is yellow.
        assertEquals(DurabilityStatus.Tier.YELLOW, DurabilityStatus.tierFor(75, MAX));
    }

    @Test
    void justBelowYellowBandIsOrange() {
        // Yellow band is (16.667, 25], so just under 16.667% remaining is orange.
        assertEquals(DurabilityStatus.Tier.ORANGE, DurabilityStatus.tierFor(84, MAX));
    }

    @Test
    void justBelowOrangeBandIsRed() {
        // Orange band is (8.333, 16.667], so just under 8.333% remaining is red.
        assertEquals(DurabilityStatus.Tier.RED, DurabilityStatus.tierFor(92, MAX));
    }

    @Test
    void clampedTerminalStateIsRed() {
        // The Unbreakable mixin clamps damage at maxDamage - 1, regardless of maxDamage's magnitude.
        assertEquals(DurabilityStatus.Tier.RED, DurabilityStatus.tierFor(MAX - 1, MAX));
    }

    @Test
    void nonDamageableItemTreatedAsFull() {
        assertEquals(100.0, DurabilityStatus.percentRemaining(0, 0));
        assertEquals(DurabilityStatus.Tier.GREY, DurabilityStatus.tierFor(0, 0));
    }
}
