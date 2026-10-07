package com.github.cerealklla.lyfe.cook;

/**
 * Cook's passive effects (design doc Section 19.3), all first-pass tunable placeholders, same
 * convention as every other numeric constant in this mod.
 */
public final class CookConstants {

    private CookConstants() {
    }

    public static int slotCapacity(int tier) {
        return FoodTierLadder.slotCapacity(tier);
    }

    public static int[] gridDims(int tier) {
        return FoodTierLadder.gridDims(tier);
    }

    /**
     * Cook's own level-&gt;max-researchable-recipe-tier table -- deliberately much more generous
     * than Crafter's (design doc, 2026-10-03 user request: "it should be a lot easier to learn a
     * Cooking Recipe"). Every tier unlocks roughly 10 levels earlier than the equipment equivalent.
     */
    public static int researchUnlockedTier(int level) {
        if (level <= 5) {
            return 1;
        } else if (level <= 12) {
            return 2;
        } else if (level <= 20) {
            return 3;
        } else if (level <= 30) {
            return 4;
        }
        return 5;
    }

    /** Live benefit readout for the Skills screen (common.skill.SkillBenefits). */
    public static java.util.List<String> benefitLines(int level) {
        return java.util.List.of(
                "Max researchable recipe tier: " + researchUnlockedTier(level)
        );
    }
}
