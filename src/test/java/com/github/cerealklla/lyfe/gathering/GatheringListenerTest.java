package com.github.cerealklla.lyfe.gathering;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

/** Benefit-line formatting for the Skills screen -- values only (design doc Section 13), never formulas. */
class GatheringListenerTest {

    @Test
    void lumberjackAtLevelThirteenMatchesTheUserSpecExample() {
        List<String> lines = GatheringListener.benefitLines(GatheringSkill.LUMBERJACK, 13);
        assertEquals("Break speed +13%", lines.get(0));
        assertEquals("Bonus yield chance 13.0%", lines.get(1));
        assertEquals("Whole-tree clear chance 5.2%", lines.get(2));
    }

    @Test
    void minerAtLevelThirteenMatchesTheUserSpecExample() {
        List<String> lines = GatheringListener.benefitLines(GatheringSkill.MINER, 13);
        assertEquals("Break speed +13%", lines.get(0));
        assertEquals("Bonus yield chance 13.0%", lines.get(1));
        assertEquals("Whole-vein clear chance 10.4%", lines.get(2));
    }

    @Test
    void bonusYieldChanceCapsAtFiftyPercent() {
        List<String> lines = GatheringListener.benefitLines(GatheringSkill.LUMBERJACK, 999);
        assertEquals("Bonus yield chance 50.0%", lines.get(1));
    }
}
