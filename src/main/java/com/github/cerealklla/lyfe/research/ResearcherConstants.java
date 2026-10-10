package com.github.cerealklla.lyfe.research;

/**
 * Researcher's passive effects (design doc Section 19.1), all linear by level. First-pass tunable
 * placeholders, same convention as every other numeric constant in this mod.
 */
public final class ResearcherConstants {

    static final double DURABILITY_SAVE_CHANCE_MIN = 0.10;
    static final double DURABILITY_SAVE_CHANCE_MAX = 0.20;

    static final double CRIT_FAIL_CHANCE_MIN = 0.05; // at level 0
    static final double CRIT_FAIL_CHANCE_MAX = 0.01; // at max level -- decreasing, not increasing

    static final double CRIT_SUCCESS_CHANCE_MIN = 0.01; // at level 0
    static final double CRIT_SUCCESS_CHANCE_MAX = 0.05; // at max level

    static final int CRIT_FAIL_DURABILITY_MULTIPLIER = 2;
    static final int CRIT_SUCCESS_POINTS_MULTIPLIER = 3;

    static final int FLAT_RESEARCH_POINTS_PER_ATTEMPT = 10;
    static final long RESEARCH_XP_PER_ATTEMPT = 10L;

    /**
     * Chance, per real durability-damage event on an equipped tool/weapon/armor piece, of a passive
     * Research Points grant (user request, 2026-10-09) -- see {@code PassiveDurabilityResearchListener}.
     */
    static final double DURABILITY_RESEARCH_CHANCE = 0.05;

    /**
     * Points granted per passive durability-damage proc -- deliberately much smaller than a real
     * Research Bench attempt's {@link #FLAT_RESEARCH_POINTS_PER_ATTEMPT} (reduced from an initial
     * 10 to 1, user request 2026-10-09, since this fires far more often and for free).
     */
    static final int PASSIVE_DURABILITY_RESEARCH_POINTS = 1;

    /**
     * At 0 Researcher bonus, a tier's item should survive roughly this many consecutive
     * un-saved attempts before breaking (2026-10-02 rebalance, user-specified: "4 consecutive
     * attempts + 1 per item Tier").
     */
    static final int BASE_ATTEMPTS_TO_BREAK = 4;

    /**
     * Expected number of fully-broken items of a tier to learn that tier's recipe from scratch
     * (2026-10-02 rebalance): Tier 1 = 3, Tier 2 = 4, ... Tier 5 = 7 on average -- an unlucky run
     * (crit-fails, bad variance) can stretch a tier or two further, e.g. ~8 at Tier 5.
     */
    static final int BASE_ITEMS_TO_LEARN = 2;

    private ResearcherConstants() {
    }

    /** 2026-10-02 rebalance: see design notes above. Replaces the old flat 5-damage/attempt rule. */
    static int attemptsToBreak(int tier) {
        return BASE_ATTEMPTS_TO_BREAK + tier;
    }

    /** 2026-10-02 rebalance: see design notes above. */
    static int itemsToLearn(int tier) {
        return BASE_ITEMS_TO_LEARN + tier;
    }

    static double durabilitySaveChance(int level, int maxLevel) {
        return lerp(DURABILITY_SAVE_CHANCE_MIN, DURABILITY_SAVE_CHANCE_MAX, level, maxLevel);
    }

    static double critFailChance(int level, int maxLevel) {
        return lerp(CRIT_FAIL_CHANCE_MIN, CRIT_FAIL_CHANCE_MAX, level, maxLevel);
    }

    static double critSuccessChance(int level, int maxLevel) {
        return lerp(CRIT_SUCCESS_CHANCE_MIN, CRIT_SUCCESS_CHANCE_MAX, level, maxLevel);
    }

    /**
     * 2026-10-02 rebalance: points/attempt x attempts/item x items/recipe, so the learn-threshold
     * and the durability-damage curve are finally derived from the same two numbers instead of
     * drifting independently (the old {@code tier * 20} threshold was reachable in a handful of
     * attempts regardless of tier, long before a high-tier item's real durability pool was ever
     * meaningfully dented).
     */
    public static int thresholdForTier(int tier) {
        return FLAT_RESEARCH_POINTS_PER_ATTEMPT * attemptsToBreak(tier) * itemsToLearn(tier);
    }

    private static double lerp(double min, double max, int level, int maxLevel) {
        double t = maxLevel <= 0 ? 0.0 : Math.max(0.0, Math.min(1.0, (double) level / maxLevel));
        return min + (max - min) * t;
    }

    /** Live benefit readout for the Skills screen (common.skill.SkillBenefits). */
    public static java.util.List<String> benefitLines(int level, int maxLevel) {
        return java.util.List.of(
                "Durability-save chance: " + pct(durabilitySaveChance(level, maxLevel)) + "%",
                "Critical-failure chance: " + pct(critFailChance(level, maxLevel)) + "%",
                "Critical-success chance: " + pct(critSuccessChance(level, maxLevel)) + "%"
        );
    }

    private static String pct(double fraction) {
        return String.format(java.util.Locale.ROOT, "%.2f", fraction * 100);
    }
}
