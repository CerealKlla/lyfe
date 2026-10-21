package com.github.cerealklla.lyfe.magician;

/**
 * Pure level-curve math for Spark of Life's Proficiency (design doc Section 24: "each level reduces
 * the mana cost by 1 and the cooldown by 0.5 seconds, down to a minimum cost of 4 and cooldown of
 * 1"). Unlike {@code huntsman.QuarryKnowledgeCurve}'s open-ended doubling curve, this one has a real
 * cap -- {@link #MAX_LEVEL}.
 *
 * <p><b>{@link #BASE_MANA_COST} retuned to 16, 2026-10-20</b> (explicit request: "Change the mana
 * cost of Spark of Life to 15 mana at level 1") -- was 10. Real side effect worth knowing: at the
 * original base, the mana-cost floor ({@link #MIN_MANA_COST}, 4) was reached at level 6, before
 * {@link #MAX_LEVEL} (8, driven by the cooldown floor instead); at this new base it's reached at
 * level 12 -- past {@link #MAX_LEVEL} entirely, so the mana cost no longer actually bottoms out
 * within the reachable level range (level 8's cost is 8, not 4). Left {@link #MAX_LEVEL} and {@link
 * #MIN_MANA_COST} both untouched since only the one number was actually asked for -- flagged here
 * rather than silently changed, in case the floor becoming unreachable wasn't intended.
 *
 * <p><b>{@link #PROGRESS_PER_LEVEL} retuned to 125, 2026-10-20</b> (explicit request: "Change the
 * scaling so it would take 1000 successful casts to be max proficiency") -- was 5. Each successful
 * cast/heal grants a flat {@link #PROGRESS_PER_CAST} (1, previously this was the same number as
 * {@code PROGRESS_PER_LEVEL} itself, a coincidence that made 1 cast = 1 level) toward raw progress;
 * {@code MAX_LEVEL * PROGRESS_PER_LEVEL} (8 * 125 = 1000) is exactly the cast count that reaches
 * {@link #MAX_LEVEL}, by construction.
 */
public final class SpellProficiencyCurve {

    public static final int MAX_LEVEL = 8;
    public static final int PROGRESS_PER_LEVEL = 125;
    public static final int PROGRESS_PER_CAST = 1;

    private static final int BASE_MANA_COST = 16;
    private static final int MIN_MANA_COST = 4;
    private static final double BASE_COOLDOWN_SECONDS = 5.0;
    private static final double MIN_COOLDOWN_SECONDS = 1.0;
    private static final double COOLDOWN_REDUCTION_PER_LEVEL = 0.5;

    private SpellProficiencyCurve() {
    }

    public static int levelForProgress(int progress) {
        return Math.min(MAX_LEVEL, Math.max(0, progress) / PROGRESS_PER_LEVEL);
    }

    public static int manaCost(int level) {
        return Math.max(MIN_MANA_COST, BASE_MANA_COST - level);
    }

    public static int cooldownTicks(int level) {
        double seconds = Math.max(MIN_COOLDOWN_SECONDS, BASE_COOLDOWN_SECONDS - level * COOLDOWN_REDUCTION_PER_LEVEL);
        return (int) Math.round(seconds * 20.0);
    }
}
