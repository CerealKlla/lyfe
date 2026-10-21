package com.github.cerealklla.lyfe.magician;

/**
 * Pure level-curve math for Spark of Life's Proficiency (design doc Section 24: "each level reduces
 * the mana cost... and the cooldown... down to a minimum cost/cooldown"). Unlike {@code
 * huntsman.QuarryKnowledgeCurve}'s open-ended doubling curve, this one has a real cap --
 * {@link #MAX_LEVEL}.
 *
 * <p><b>Reworked 2026-10-21, explicit user spec</b> -- "I want to add two config variables for
 * spells, MinManaCost and MinCooldown... I want the max proficiency for any spell to be 25...
 * Other things may also improve mana cost or cooldown, so we don't want proficiency alone to allow
 * a player to reach the epitome, but we want the spell minimums strictly enforced." This splits
 * what used to be one pair of constants into two genuinely different roles:
 * <ul>
 *   <li>{@link #MIN_MANA_COST}/{@link #MIN_COOLDOWN_SECONDS} -- a hard floor meant to be enforced
 *   across <i>every</i> mana-cost/cooldown-reducing source combined (Proficiency, and whatever
 *   future gear/Rune/Crafting-Quality bonus eventually also touches these), not just this curve
 *   alone. {@link #MAX_LEVEL} = 25 is likewise meant to be shared by every future spell's own
 *   Proficiency curve, not a Spark-of-Life-specific number.</li>
 *   <li>{@link #PROFICIENCY_END_MANA_COST}/{@link #PROFICIENCY_END_COOLDOWN_SECONDS} -- where
 *   Spark of Life's own Proficiency curve alone actually bottoms out at {@link #MAX_LEVEL} (8 mana,
 *   3.0s cooldown) -- deliberately short of the hard floor (4 mana, 1.0s), leaving real headroom
 *   for something else to close later rather than letting Proficiency alone reach the floor.</li>
 * </ul>
 * {@link #manaCost}/{@link #cooldownTicks} still clamp to the hard floor regardless, as a safety
 * net for whatever eventually stacks on top of the curve's own value, even though nothing does yet.
 *
 * <p>{@link #PROGRESS_PER_LEVEL} keeps the total cast count needed to reach {@link #MAX_LEVEL} at
 * 1000 (the explicit 2026-10-20 "1000 successful casts to be max proficiency" spec), just
 * redistributed across the new, larger level count -- 1000 / 25 = 40 progress per level, each
 * successful cast/heal still granting a flat {@link #PROGRESS_PER_CAST} (1) toward it.
 */
public final class SpellProficiencyCurve {

    // Shared across every spell's own Proficiency curve, not just Spark of Life's.
    public static final int MAX_LEVEL = 25;

    public static final int PROGRESS_PER_LEVEL = 40;
    public static final int PROGRESS_PER_CAST = 1;

    // Spark of Life's own config (see class doc for the base/end/floor distinction).
    private static final int BASE_MANA_COST = 16;
    private static final int PROFICIENCY_END_MANA_COST = 8;
    private static final int MIN_MANA_COST = 4;
    private static final double BASE_COOLDOWN_SECONDS = 5.0;
    private static final double PROFICIENCY_END_COOLDOWN_SECONDS = 3.0;
    private static final double MIN_COOLDOWN_SECONDS = 1.0;

    private SpellProficiencyCurve() {
    }

    public static int levelForProgress(int progress) {
        return Math.min(MAX_LEVEL, Math.max(0, progress) / PROGRESS_PER_LEVEL);
    }

    /**
     * Fractional progress (0.0-1.0) toward the next level, for the transient progress bar (added
     * 2026-10-21 for {@code magician.client.SpellProficiencyBarOverlay} -- mirrors {@code
     * huntsman.QuarryKnowledgeCurve#fractionToNextLevel} exactly, just against this curve's flat
     * per-level cost instead of a doubling one).
     */
    public static double fractionToNextLevel(int progress) {
        if (levelForProgress(progress) >= MAX_LEVEL) {
            return 1.0;
        }
        int progressThisLevel = Math.max(0, progress) % PROGRESS_PER_LEVEL;
        return Math.clamp((double) progressThisLevel / PROGRESS_PER_LEVEL, 0.0, 1.0);
    }

    /** Linearly interpolates {@link #BASE_MANA_COST} down to {@link #PROFICIENCY_END_MANA_COST} over 0..{@link #MAX_LEVEL}, clamped to the hard {@link #MIN_MANA_COST} floor. */
    public static int manaCost(int level) {
        double fraction = Math.max(0, Math.min(level, MAX_LEVEL)) / (double) MAX_LEVEL;
        double raw = BASE_MANA_COST - (BASE_MANA_COST - PROFICIENCY_END_MANA_COST) * fraction;
        return Math.max(MIN_MANA_COST, (int) Math.round(raw));
    }

    /** Linearly interpolates {@link #BASE_COOLDOWN_SECONDS} down to {@link #PROFICIENCY_END_COOLDOWN_SECONDS} over 0..{@link #MAX_LEVEL}, clamped to the hard {@link #MIN_COOLDOWN_SECONDS} floor. */
    public static int cooldownTicks(int level) {
        double fraction = Math.max(0, Math.min(level, MAX_LEVEL)) / (double) MAX_LEVEL;
        double raw = BASE_COOLDOWN_SECONDS - (BASE_COOLDOWN_SECONDS - PROFICIENCY_END_COOLDOWN_SECONDS) * fraction;
        double seconds = Math.max(MIN_COOLDOWN_SECONDS, raw);
        return (int) Math.round(seconds * 20.0);
    }
}
