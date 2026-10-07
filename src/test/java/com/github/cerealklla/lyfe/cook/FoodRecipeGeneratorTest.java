package com.github.cerealklla.lyfe.cook;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

class FoodRecipeGeneratorTest {

    @Test
    void trackBTierTwoAndAboveRequiresThePreviousTierDish() {
        Random random = new Random(42);
        for (int tier = 2; tier <= FoodTierLadder.MAX_TIER; tier++) {
            Identifier resultId = FoodTierLadder.trackB(tier);
            GeneratedFoodRecipe recipe = FoodRecipeGenerator.generate(resultId, tier, random);
            Identifier previousDish = FoodTierLadder.trackB(tier - 1);
            assertTrue(recipe.components().containsKey(previousDish), "tier " + tier + " missing chain link");
            int qty = recipe.components().get(previousDish);
            assertTrue(qty >= 1 && qty <= 2, "chain quantity out of [1,2]: " + qty);
        }
    }

    @Test
    void trackBTierOneHasNoChainRequirement() {
        Random random = new Random(1);
        GeneratedFoodRecipe recipe = FoodRecipeGenerator.generate(FoodTierLadder.trackB(1), 1, random);
        assertTrue(recipe.components().size() >= 1);
    }

    /**
     * 2026-10-06: every Track B recipe at every tier now requires one of each of that tier's own
     * Track A items as a guaranteed (non-randomized) component -- "a feast uses everything."
     */
    @Test
    void trackBRequiresEveryTrackAItemAtItsOwnTier() {
        Random random = new Random(5);
        for (int tier = FoodTierLadder.MIN_TIER; tier <= FoodTierLadder.MAX_TIER; tier++) {
            Identifier resultId = FoodTierLadder.trackB(tier);
            GeneratedFoodRecipe recipe = FoodRecipeGenerator.generate(resultId, tier, random);
            for (Identifier trackAItem : FoodTierLadder.trackA(tier)) {
                assertTrue(recipe.components().containsKey(trackAItem),
                        "tier " + tier + " missing required Track A item " + trackAItem);
            }
        }
    }
}
