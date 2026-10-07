package com.github.cerealklla.lyfe.cook;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.cerealklla.lyfe.craft.CrafterConstants;

import org.junit.jupiter.api.Test;

class CookConstantsTest {

    /** "It should be a lot easier to learn a Cooking Recipe" (2026-10-03) -- made concrete: Cook's own gate unlocks every tier at a meaningfully lower level than Crafter's equivalent. */
    @Test
    void cookResearchGateIsMeaningfullyEasierThanCrafters() {
        for (int level = 0; level <= 50; level++) {
            assertTrue(CookConstants.researchUnlockedTier(level) >= CrafterConstants.researchUnlockedTier(level),
                    "at level " + level + " Cook should unlock at least as much as Crafter");
        }
        // And strictly easier somewhere in the low-to-mid range, not just coincidentally equal everywhere.
        assertTrue(CookConstants.researchUnlockedTier(15) > CrafterConstants.researchUnlockedTier(15));
    }
}
