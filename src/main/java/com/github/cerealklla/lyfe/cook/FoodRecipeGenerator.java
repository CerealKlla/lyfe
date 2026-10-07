package com.github.cerealklla.lyfe.cook;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import net.minecraft.resources.Identifier;

/**
 * Pure food-recipe-generation logic, mirroring {@code craft.RecipeGenerator} (design doc Section
 * 19.4, extended to food 2026-10-03). **Only ever called for Track B ("signature dish") results now**
 * (2026-10-06) -- Track A (real vanilla staples) are a fixed, non-randomized recipe instead, see
 * {@link VanillaFoodRecipes}. A generated recipe uses 50%-100% of its tier's slot capacity ({@code
 * FoodTierLadder#slotCapacity}) for its *random* filler ingredients, each one's tier rolled from the
 * same 60/25/10/2-by-distance weighting tools use -- on top of (not instead of) its guaranteed
 * requirements below, so {@code slotsUsed()} can legitimately exceed the nominal capacity once those
 * are counted; nothing enforces capacity as a hard UI limit (confirmed -- neither it nor {@code
 * gridDims} is read anywhere in the actual ingredient-list screen), it only ever shaped how many
 * random extras got rolled.
 *
 * <p><b>Two guaranteed, non-randomized requirements</b>:
 * <ul>
 *   <li><b>The entree chain</b> (2026-10-03): every tier &ge;2 requires **1 or more** of the
 *   previous tier's own dish -- generalized from equipment's fixed exactly-1 upgrade-chain rule, per
 *   the user's own "one or more previous crafted dishes can be components" wording.</li>
 *   <li><b>Every Track A item at this tier</b> (2026-10-06, explicit user request/choice: "All of
 *   that tier's Track A items required" -- "a feast uses everything"): one of each, guaranteed, in
 *   addition to the chain requirement and the random fillers.</li>
 * </ul>
 */
final class FoodRecipeGenerator {

    private static final int MIN_QUANTITY = 1;
    private static final int MAX_QUANTITY = 4;
    private static final int MIN_CHAIN_QUANTITY = 1;
    private static final int MAX_CHAIN_QUANTITY = 2;
    private static final int TRACK_A_REQUIREMENT_QUANTITY = 1;

    private FoodRecipeGenerator() {
    }

    static GeneratedFoodRecipe generate(Identifier resultId, int tier, Random random) {
        int capacity = FoodTierLadder.slotCapacity(tier);
        int minSlots = Math.max(1, (int) Math.ceil(capacity * 0.5));
        int targetSlots = minSlots + (minSlots < capacity ? random.nextInt(capacity - minSlots + 1) : 0);

        Map<Identifier, Integer> components = new LinkedHashMap<>();
        int attempts = 0;
        while (components.size() < targetSlots && attempts < targetSlots * 50) {
            attempts++;
            int materialTier = rollWeightedTier(tier, random);
            List<Identifier> pool = FoodMaterialPool.poolForTier(materialTier);
            Identifier pick = pool.get(random.nextInt(pool.size()));
            int qty = MIN_QUANTITY + random.nextInt(MAX_QUANTITY - MIN_QUANTITY + 1);
            components.putIfAbsent(pick, qty);
        }

        if (tier >= 2) {
            int chainQty = MIN_CHAIN_QUANTITY + random.nextInt(MAX_CHAIN_QUANTITY - MIN_CHAIN_QUANTITY + 1);
            components.put(FoodTierLadder.trackB(tier - 1), chainQty);
        }
        for (Identifier trackAItem : FoodTierLadder.trackA(tier)) {
            components.put(trackAItem, TRACK_A_REQUIREMENT_QUANTITY);
        }

        return new GeneratedFoodRecipe(resultId, tier, components);
    }

    private static int rollWeightedTier(int targetTier, Random random) {
        int[] weights = new int[FoodTierLadder.MAX_TIER + 1];
        int total = 0;
        for (int t = FoodTierLadder.MIN_TIER; t <= FoodTierLadder.MAX_TIER; t++) {
            int distance = Math.abs(t - targetTier);
            int weight = switch (distance) {
                case 0 -> 60;
                case 1 -> 25;
                case 2 -> 10;
                default -> 2;
            };
            weights[t] = weight;
            total += weight;
        }
        int roll = random.nextInt(total);
        int cumulative = 0;
        for (int t = FoodTierLadder.MIN_TIER; t <= FoodTierLadder.MAX_TIER; t++) {
            cumulative += weights[t];
            if (roll < cumulative) {
                return t;
            }
        }
        return targetTier;
    }
}
