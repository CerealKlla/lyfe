package com.github.cerealklla.lyfe.craft;

/**
 * Crafter's passive effects (design doc Section 19.2/19.2.1), all linear by level. First-pass
 * tunable placeholders, same convention as every other numeric constant in this mod.
 */
public final class CrafterConstants {

    static final double COMPONENT_SAVE_CHANCE_MIN = 0.05;
    static final double COMPONENT_SAVE_CHANCE_MAX = 0.30;
    static final int MAX_COMPONENTS_SAVED_PER_CRAFT = 2;

    static final double QUALITY_LEVEL_MIN = 10.0;
    static final double QUALITY_LEVEL_MAX = 100.0;

    static final double QUALITY_STRUCTURE_BONUS_MIN = 5.0; // Tier 1
    static final double QUALITY_STRUCTURE_BONUS_MAX = 30.0; // Tier 5

    private CrafterConstants() {
    }

    static double componentSaveChance(int level, int maxLevel) {
        double t = maxLevel <= 0 ? 0.0 : Math.max(0.0, Math.min(1.0, (double) level / maxLevel));
        return COMPONENT_SAVE_CHANCE_MIN + (COMPONENT_SAVE_CHANCE_MAX - COMPONENT_SAVE_CHANCE_MIN) * t;
    }

    static double qualityFromLevel(int level, int maxLevel) {
        double t = maxLevel <= 0 ? 0.0 : Math.max(0.0, Math.min(1.0, (double) level / maxLevel));
        return QUALITY_LEVEL_MIN + (QUALITY_LEVEL_MAX - QUALITY_LEVEL_MIN) * t;
    }

    /** Section 19.2.1: 5% at Tier 1 structure, up to 30% at Tier 5 -- linear step assumed, flagged tunable. */
    static double structureBonus(int structureTier) {
        double t = (Math.max(1, Math.min(5, structureTier)) - 1) / 4.0;
        return QUALITY_STRUCTURE_BONUS_MIN + (QUALITY_STRUCTURE_BONUS_MAX - QUALITY_STRUCTURE_BONUS_MIN) * t;
    }

    /**
     * Crafter's level->max-researchable-recipe-tier table (added 2026-10-02) -- gates which recipe
     * *tiers* are even eligible to research at the Research Bench using a real item, so a player
     * can't learn a Tier 5 recipe at level 0 and sit on it until they can build that high. Does
     * **not** gate crafting itself (removed entirely 2026-10-03, explicit user correction -- a
     * player who knows a recipe can always craft it at a tier-capable station, no Crafter-level
     * check at all). The one way to research above this gate is a Research Note
     * ({@code research.ResearchNoteItem}), which deliberately never checks it.
     */
    public static int researchUnlockedTier(int level) {
        if (level <= 10) {
            return 1;
        } else if (level <= 20) {
            return 2;
        } else if (level <= 30) {
            return 3;
        } else if (level <= 40) {
            return 4;
        }
        return 5;
    }

    /** Live benefit readout for the Skills screen (common.skill.SkillBenefits). */
    public static java.util.List<String> benefitLines(int level, int maxLevel) {
        double saveChancePercent = componentSaveChance(level, maxLevel) * 100;
        double quality = qualityFromLevel(level, maxLevel);
        return java.util.List.of(
                "Component-save chance: " + String.format(java.util.Locale.ROOT, "%.1f", saveChancePercent) + "%",
                "Quality range: " + (int) QUALITY_LEVEL_MIN + "-" + (int) QUALITY_LEVEL_MAX
                        + "% (current: " + String.format(java.util.Locale.ROOT, "%.1f", quality) + "%)"
        );
    }
}
