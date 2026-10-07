package com.github.cerealklla.lyfe.rest;

/**
 * The exact scaling the user specified, 2026-10-03: resting (sleeping -- {@code
 * Player#isSleeping()}, not just standing near a bed -- or sitting on a {@code StoolBlock}) for
 * 5-90 real seconds grants a Well Rested duration that scales linearly from 3-20 minutes. Below
 * the 5-second floor, no status is granted at all -- there is no partial reward for a rest that was
 * immediately interrupted. (Floor lowered from 10 -> 5 seconds, same day, explicit user request.)
 */
public final class RestConstants {

    private RestConstants() {
    }

    public static final long MIN_LAY_SECONDS = 5;
    public static final long MAX_LAY_SECONDS = 90;

    public static final int MIN_WELL_RESTED_TICKS = 3 * 60 * 20;
    public static final int MAX_WELL_RESTED_TICKS = 20 * 60 * 20;

    /** While Well Rested is active, all XP gains are multiplied by this (2026-10-03 user request). */
    public static final long WELL_RESTED_XP_MULTIPLIER = 2L;
}
