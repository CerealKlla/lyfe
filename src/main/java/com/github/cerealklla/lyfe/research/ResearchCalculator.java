package com.github.cerealklla.lyfe.research;

import java.util.Random;

/**
 * Pure research-attempt roll logic (design doc Section 19.1), separated from {@code ResearchMenu}
 * so it's unit-testable without a live server/player.
 */
public final class ResearchCalculator {

    private ResearchCalculator() {
    }

    /**
     * {@code basePoints} is caller-supplied (10 for a real Research Bench attempt, 1 for a Research
     * Note -- see {@code ResearchNoteItem}) so both paths share the exact same crit-fail/crit-success
     * chance curves (the user's explicit ask, 2026-10-03: "the Researcher skill % bonuses ... should
     * also apply if using a Researcher Note") while still scaling their own point totals correctly.
     */
    public static ResearchOutcome roll(int researcherLevel, int maxLevel, int basePoints, Random random) {
        boolean criticalFailure = random.nextDouble() < ResearcherConstants.critFailChance(researcherLevel, maxLevel);
        if (criticalFailure) {
            return new ResearchOutcome(0, true, false, false);
        }

        boolean criticalSuccess = random.nextDouble() < ResearcherConstants.critSuccessChance(researcherLevel, maxLevel);
        int points = basePoints * (criticalSuccess ? ResearcherConstants.CRIT_SUCCESS_POINTS_MULTIPLIER : 1);

        boolean durabilitySaved = random.nextDouble() < ResearcherConstants.durabilitySaveChance(researcherLevel, maxLevel);
        return new ResearchOutcome(points, false, criticalSuccess, durabilitySaved);
    }

    /**
     * Durability damage for a non-food research attempt -- a flat portion derived from the item's
     * own max durability (so it breaks in roughly {@link ResearcherConstants#attemptsToBreak(int)}
     * un-saved attempts regardless of how large that tier's real durability pool is, e.g. a
     * Golden Sword's unusually low vanilla durability vs. a Netherite Sword's unusually high one)
     * plus a random portion scaled to what's already missing, doubled on a critical failure.
     */
    static int durabilityDamage(int maxDamage, int currentDamage, int tier, boolean criticalFailure, Random random) {
        int flat = Math.max(1, maxDamage / ResearcherConstants.attemptsToBreak(tier));
        int amount = flat + (currentDamage > 0 ? random.nextInt(Math.max(1, currentDamage / 4) + 1) : 0);
        return criticalFailure ? amount * ResearcherConstants.CRIT_FAIL_DURABILITY_MULTIPLIER : amount;
    }
}
