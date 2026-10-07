package com.github.cerealklla.lyfe.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Dispatch sanity for the Skills screen (design doc Section 13) -- one case per skill family.
 * {@code player} is {@code null} throughout: none of these cases is Merchant, the only skill that
 * actually reads it (for the player's real Yconomics Coin Purse tier).
 */
class SkillBenefitsTest {

    @Test
    void lumberjackDispatchesToGatheringListener() {
        List<String> lines = SkillBenefits.describe(Skills.LUMBERJACK_ID, null, 13, 50);
        assertEquals("Break speed +13%", lines.get(0));
    }

    @Test
    void historianHasNoMechanicSoShowsAPlaceholder() {
        List<String> lines = SkillBenefits.describe(Skills.HISTORIAN_ID, null, 0, 50);
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).toLowerCase().contains("coming soon"));
    }

    @Test
    void reincarnationDispatchesToSlotList() {
        List<String> lines = SkillBenefits.describe(Skills.REINCARNATION_ID, null, 1, 14);
        assertEquals(2, lines.size()); // 1 protected + 1 next-unlock line
    }

    @Test
    void crafterAndResearcherDispatchWithMaxLevelAware() {
        assertFalse(SkillBenefits.describe(Skills.CRAFTER_ID, null, 25, 50).isEmpty());
        assertFalse(SkillBenefits.describe(Skills.RESEARCHER_ID, null, 25, 50).isEmpty());
    }
}
