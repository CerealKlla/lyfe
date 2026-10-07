package com.github.cerealklla.lyfe.heartiness;

/**
 * All placeholder/tunable magnitudes for the Heartiness skill (design doc, 2026-10-03 user
 * request), flagged as such per this mod's standing convention -- none of these were specified to
 * the exact number, only the three shapes they follow: bonus max health growing linearly to 3x
 * vanilla's base by max level, a rapid-heal proc chance that only exists past level 25 and grows
 * from there, and that proc's own fixed heal-over-time shape.
 */
public final class HeartinessConstants {

    private HeartinessConstants() {
    }

    /** Vanilla's own base max health (10 hearts) -- the 1x point of the growth curve. */
    public static final float BASE_MAX_HEALTH = 20.0F;

    /** "up to tripple vanilla max" -- 3x base, reached at max level. */
    public static final float MAX_HEALTH_MULTIPLIER = 3.0F;

    /** Rapid Recovery only has a chance to proc starting at this level. */
    public static final int RAPID_HEAL_MIN_LEVEL = 25;

    /** Chance at max level -- grows linearly from 0% at RAPID_HEAL_MIN_LEVEL to this (2026-10-03 playtest correction: was 50%). */
    public static final double RAPID_HEAL_MAX_CHANCE = 0.20;

    /** "gain 4 hearts" -- 8 health points, over 5 seconds (2026-10-03 playtest correction: was 3). */
    public static final float RAPID_HEAL_TOTAL_AMOUNT = 8.0F;
    public static final int RAPID_HEAL_DURATION_TICKS = 100;

    /**
     * Base XP per whole point of health actually recovered, from any source (2026-10-03 playtest
     * correction -- originally only mob-caused missing health granted anything at all; now every
     * source does, at this base rate, with mob-caused recovery multiplied -- see below).
     */
    public static final long XP_PER_HEALTH_POINT = 5L;

    /** Health recovered that's attributable to mob-caused damage grants this many times the base rate. */
    public static final long MOB_DAMAGE_XP_MULTIPLIER = 5L;
}
