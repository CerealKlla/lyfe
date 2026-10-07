package com.github.cerealklla.lyfe.cook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

class FoodTierLadderTest {

    @Test
    void trackBFormsAUniqueChainAcrossAllTiers() {
        var seen = new java.util.HashSet<Identifier>();
        for (int tier = FoodTierLadder.MIN_TIER; tier <= FoodTierLadder.MAX_TIER; tier++) {
            Identifier id = FoodTierLadder.trackB(tier);
            assertTrue(seen.add(id), "duplicate Track B id at tier " + tier);
            assertTrue(FoodTierLadder.isTrackB(id));
            assertEquals(tier, FoodTierLadder.tierOf(id));
        }
    }

    @Test
    void trackAItemsResolveToTheirOwnTier() {
        for (int tier = FoodTierLadder.MIN_TIER; tier <= FoodTierLadder.MAX_TIER; tier++) {
            for (Identifier id : FoodTierLadder.trackA(tier)) {
                assertEquals(tier, FoodTierLadder.tierOf(id));
                assertFalse(FoodTierLadder.isTrackB(id));
            }
        }
    }

    @Test
    void slotCapacityMatchesTheGridAreas() {
        assertEquals(1, FoodTierLadder.slotCapacity(1));
        assertEquals(6, FoodTierLadder.slotCapacity(2));
        assertEquals(15, FoodTierLadder.slotCapacity(3));
        assertEquals(24, FoodTierLadder.slotCapacity(4));
        assertEquals(35, FoodTierLadder.slotCapacity(5));
    }

    @Test
    void allResultIdsIncludesBothTracksForEveryTier() {
        var ids = FoodTierLadder.allResultIds();
        int expected = 0;
        for (int tier = FoodTierLadder.MIN_TIER; tier <= FoodTierLadder.MAX_TIER; tier++) {
            expected += FoodTierLadder.trackA(tier).size() + 1;
        }
        assertEquals(expected, ids.size());
    }
}
