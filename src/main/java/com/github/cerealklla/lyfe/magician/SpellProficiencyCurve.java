package com.github.cerealklla.lyfe.magician;

/**
 * Pure level-curve math for Spark of Life's Proficiency (design doc Section 24: "each level reduces
 * the mana cost by 1 and the cooldown by 0.5 seconds, down to a minimum cost of 4 and cooldown of
 * 1"). Unlike {@code huntsman.QuarryKnowledgeCurve}'s open-ended doubling curve, this one has a real
 * cap -- {@link #MAX_LEVEL} is derived mechanically from the spec's own numbers: the mana-cost floor
 * (10 -&gt; 4, one point per level) is reached at level 6, but the cooldown floor (5.0s -&gt; 1.0s,
 * half a second per level) isn't reached until level 8 -- so level 8 is where the spell is fully
 * mastered, not level 6.
 *
 * <p>Progress-per-level is a flat, explicitly tunable placeholder ({@link #PROGRESS_PER_LEVEL}),
 * same convention as every other first-pass numeric constant in this mod -- the spec names the
 * cost/cooldown reduction per level precisely, but never says how many casts/heals/combat-survivals
 * a level actually takes.
 */
public final class SpellProficiencyCurve {

    public static final int MAX_LEVEL = 8;
    public static final int PROGRESS_PER_LEVEL = 5;

    private static final int BASE_MANA_COST = 10;
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
