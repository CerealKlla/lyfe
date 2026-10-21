package com.github.cerealklla.lyfe.craft;

/**
 * Crafter's passive effects (design doc Section 19.2/19.2.1), all linear by level. First-pass
 * tunable placeholders, same convention as every other numeric constant in this mod.
 */
public final class CrafterConstants {

    /** Flat Crafter XP for crafting any item through {@code craft.CraftingStructureMenu} -- no tier/item scaling at all. Also the "would crafting this grant X" baseline {@code repair.RepairFunding} scales its own 0-60% repair-XP reward off (2026-10-10). */
    public static final int CRAFTER_XP_PER_CRAFT = 20;

    static final double COMPONENT_SAVE_CHANCE_MIN = 0.05;
    static final double COMPONENT_SAVE_CHANCE_MAX = 0.30;
    static final int MAX_COMPONENTS_SAVED_PER_CRAFT = 2;

    /**
     * How much a single save actually removes -- real question, 2026-10-20: "1/64 seems pointless to
     * me," after a Leaf Litter -> Charcoal craft (specific_components cost 64) only ever saved a flat
     * 1 unit, same as a normal equipment recipe's typical 1-4 cost. Changed from a flat -1 to a
     * percentage of the component's own required amount, floored at 1 -- a small equipment recipe
     * still saves exactly 1 (5% of 2 or 4 rounds up to 1, unchanged from the old behavior), while a
     * bulk MiscRecipes-style conversion saves a proportionally meaningful amount (5% of 64 rounds up
     * to 4). Explicit user choices: percentage over flat or leave-alone, then "rounded up of course"
     * over nearest/down.
     */
    static final double COMPONENT_SAVE_FRACTION = 0.05;

    static final double QUALITY_LEVEL_MIN = 10.0;
    static final double QUALITY_LEVEL_MAX = 100.0;

    static final double QUALITY_STRUCTURE_BONUS_MIN = 5.0; // Tier 1
    static final double QUALITY_STRUCTURE_BONUS_MAX = 30.0; // Tier 5

    /** Max Crafter level + max structure tier, 100 + 30 -- the real ceiling {@code quality} can ever reach. Public so {@code magician.FocusStatRoller} can scale its own Quality-nudge chance against a real number instead of a duplicated magic constant. */
    public static final double MAX_ACHIEVABLE_QUALITY = QUALITY_LEVEL_MAX + QUALITY_STRUCTURE_BONUS_MAX;

    private CrafterConstants() {
    }

    static double componentSaveChance(int level, int maxLevel) {
        double t = maxLevel <= 0 ? 0.0 : Math.max(0.0, Math.min(1.0, (double) level / maxLevel));
        return COMPONENT_SAVE_CHANCE_MIN + (COMPONENT_SAVE_CHANCE_MAX - COMPONENT_SAVE_CHANCE_MIN) * t;
    }

    /**
     * See {@link #COMPONENT_SAVE_FRACTION}'s own doc -- rounded up (explicit user correction, "rounded
     * up of course": a save should never feel like it rounded itself away), never more than what's
     * actually required.
     */
    static int amountSaved(int requiredAmount) {
        return Math.min(requiredAmount, Math.max(1, (int) Math.ceil(requiredAmount * COMPONENT_SAVE_FRACTION)));
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
