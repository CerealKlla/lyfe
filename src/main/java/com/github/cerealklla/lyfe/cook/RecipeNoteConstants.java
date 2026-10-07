package com.github.cerealklla.lyfe.cook;

import java.util.Random;

import net.minecraft.resources.Identifier;

/**
 * First-pass tunable placeholders for loot-found Recipe Notes -- direct mirror of {@code
 * research.ResearchNoteConstants}, deliberately more common (2026-10-03 user request: "it should
 * be a lot easier to learn a Cooking Recipe") -- a higher pool chance and weights skewed even more
 * heavily toward low tiers than the equipment version's {@code {40,25,15,12,8}}.
 */
public final class RecipeNoteConstants {

    public static final float POOL_CHANCE = 0.35F;

    /** Index 0 = Tier 1 .. index 4 = Tier 5. */
    public static final int[] TIER_WEIGHTS = {50, 25, 13, 8, 4};

    private RecipeNoteConstants() {
    }

    /** A random valid Recipe Note target for {@code tier} -- one of that tier's Track A or Track B results. */
    public static Identifier randomTargetForTier(int tier, Random random) {
        java.util.List<Identifier> candidates = new java.util.ArrayList<>(FoodTierLadder.trackA(tier));
        candidates.add(FoodTierLadder.trackB(tier));
        return candidates.get(random.nextInt(candidates.size()));
    }
}
