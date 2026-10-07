package com.github.cerealklla.lyfe.craft;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import net.minecraft.resources.Identifier;

/**
 * Pure recipe-generation logic (design doc Section 19.4) -- zero human input, confirmed 2026-10-02.
 * A generated recipe uses a random number of distinct material components between 50% and 100% of
 * its tier's slot capacity ({@link EquipmentTierLadder#slotCapacity}); each component's material
 * tier is rolled from a distribution weighted toward the recipe's own tier (a Tier 1 item draws
 * overwhelmingly from Tier 1's material pool, rarely Tier 5/6), so a Tier 1 sword essentially never
 * rolls Diamond -- confirmed intent, not literally impossible, just heavily weighted against.
 * Exact weighting curve/quantity ranges are first-pass tunable placeholders, same convention as
 * every other numeric constant in this mod.
 *
 * <p>Upgrade-chain recipes (2026-10-03, explicit user request): every tier above 0 also requires
 * exactly 1 of the PREVIOUS tier's own finished item -- "a Stone sword needs a wood sword + the
 * mats," mirroring vanilla's own real Netherite-upgrade mechanic (Diamond tool + Netherite Ingot).
 * This is a guaranteed, non-randomized component on top of the material roll, not part of it -- one
 * slot of the tier's capacity is reserved for it so the existing {@code slotsUsed() <= capacity}
 * invariant still holds exactly.
 */
final class RecipeGenerator {

    private static final int MIN_QUANTITY = 1;
    private static final int MAX_QUANTITY = 4;

    private RecipeGenerator() {
    }

    /**
     * {@code previousTierItem} is the guaranteed upgrade-chain component (the same kind of
     * equipment, one tier down) -- {@code null} at tier 0, which has no previous tier. Taking the
     * item id directly rather than a {@code ToolType} (2026-10-04) lets this one method generate
     * both tool/weapon and armor recipes -- armor has no {@code ToolType} of its own at all.
     */
    static GeneratedRecipe generate(Identifier resultId, int tier, Identifier previousTierItem, Random random) {
        int capacity = EquipmentTierLadder.slotCapacity(tier);
        // Tier 0 (Wood) bypasses the normal 50%-100%-of-capacity formula (2026-10-03, user request:
        // "can we set that from 1-3 instead?") -- that formula's floor (ceil(capacity * 0.5)) would
        // only ever give 2-3 for a capacity of 3, never 1, so Wood gets its own flat uniform roll
        // across the user's actual requested range instead. Every other tier reserves exactly 1 slot
        // of its capacity for the guaranteed upgrade-chain component added below.
        int materialCapacity = tier == 0 ? capacity : capacity - 1;
        int targetSlots;
        if (tier == 0) {
            targetSlots = 1 + random.nextInt(materialCapacity);
        } else {
            int minSlots = Math.max(1, (int) Math.ceil(materialCapacity * 0.5));
            targetSlots = minSlots + (minSlots < materialCapacity ? random.nextInt(materialCapacity - minSlots + 1) : 0);
        }

        Map<Identifier, Integer> specificComponents = new LinkedHashMap<>();
        Map<String, Integer> genericComponents = new LinkedHashMap<>();
        int attempts = 0;
        // Budget multiplier raised 10 -> 50 on 2026-10-03 after shrinking MaterialPool's tier 3/4
        // lists (removing Nether/structure-gated items per user request) -- a heavily tier-skewed
        // roll re-picks an already-collected item from a now-smaller pool far more often before it
        // reaches a rarer tier's distinct items, so the old budget started under-filling high-tier
        // recipes (confirmed via RecipeGeneratorTest's fixed-seed Tier 5 case).
        while (specificComponents.size() + genericComponents.size() < targetSlots && attempts < targetSlots * 50) {
            attempts++;
            // Tier 0 (Wood) never uses the weighted tail at all -- 2026-10-03, real bug found live:
            // even distance 4-5 away, rollWeightedTier's flat "default -> 2" bucket let a Tier 0
            // recipe roll Tier 4's own material (a real Blaze Rod requirement on a Wood tool was
            // reported). "Incredibly cheap" has to be a guarantee, not just a statistical near-
            // certainty -- Tier 0 draws exclusively from its own pool.
            int materialTier = tier == 0 ? 0 : rollWeightedTier(tier, random);
            List<MaterialPool.MaterialPick> picks = MaterialPool.picksForTier(materialTier);
            MaterialPool.MaterialPick pick = picks.get(random.nextInt(picks.size()));
            int qty = MIN_QUANTITY + random.nextInt(MAX_QUANTITY - MIN_QUANTITY + 1);
            switch (pick) {
                case MaterialPool.MaterialPick.Specific specific -> specificComponents.putIfAbsent(specific.id(), qty);
                case MaterialPool.MaterialPick.Generic generic -> genericComponents.putIfAbsent(generic.groupName(), qty);
            }
        }

        if (tier > 0 && previousTierItem != null) {
            specificComponents.put(previousTierItem, 1);
        }

        return new GeneratedRecipe(resultId, tier, specificComponents, genericComponents);
    }

    /**
     * Weighted toward {@code targetTier}: the target tier itself is heavily favored, with
     * exponentially decaying odds for each tier of distance away.
     */
    private static int rollWeightedTier(int targetTier, Random random) {
        int[] weights = new int[EquipmentTierLadder.MAX_TIER + 1];
        int total = 0;
        for (int t = 0; t <= EquipmentTierLadder.MAX_TIER; t++) {
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
        for (int t = 0; t <= EquipmentTierLadder.MAX_TIER; t++) {
            cumulative += weights[t];
            if (roll < cumulative) {
                return t;
            }
        }
        return targetTier;
    }
}
