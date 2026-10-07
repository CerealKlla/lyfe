package com.github.cerealklla.lyfe.swim;

/**
 * Constants for the decoupled-air mechanism (design doc Section 5's Swimmer entry). Mirrors
 * {@code .hunger.HungerConstants}'s own shape: real vanilla units (ticks, confirmed against the
 * decompiled {@code LivingEntity#baseTick}/{@code getMaxAirSupply} source -- vanilla's own air cap
 * is a hardcoded {@code 300}, never attribute-driven, and drowning damage fires once the real value
 * hits {@code -20}) stay fixed/absolute regardless of a player's current true max, same reasoning
 * {@code HungerConstants} already documents for its own thresholds.
 */
final class AirConstants {

    static final int BASE_MAX_AIR = 300; // vanilla Entity#getMaxAirSupply()
    static final int MAX_AIR_AT_MAX_LEVEL = 900; // design doc: "two full bars beyond vanilla's default" -- 3x total, same ratio HungerConstants uses

    // The value the real air supply gets pinned to at the end of every tick, so vanilla's own
    // "<=-20" drowning check never fires against it (vanilla only ever decrements by 1/tick, so
    // resetting to this full value every tick-end means the real value never dips below 299 before
    // being measured and repinned) -- same role HungerConstants#REAL_FOOD_PIN plays for hunger.
    static final int REAL_AIR_PIN = BASE_MAX_AIR;

    static final int TICKS_PER_BUBBLE = BASE_MAX_AIR / 10; // vanilla Gui#NUM_AIR_BUBBLES

    // Flagged tunable, like every other XP rate in this mod -- 2 XP per whole bubble lost (not per
    // raw air-tick -- a bubble is the real unit of "air lost" the player perceives). Banked only
    // when a bubble boundary is actually crossed, so this stays a small, bounded-per-dive total
    // rather than a scaled-up per-tick rate (corrected 2026-10-02 after live testing showed the
    // original per-tick rate reached max level in ~5 minutes).
    static final long XP_PER_BUBBLE_LOST = 2;

    // Passive recovery-rate growth: vanilla's own fixed +4 ticks/tick while breathing
    // (LivingEntity#increaseAirSupply) up to double that at max Swimmer level.
    static final int BASE_RECOVERY_PER_TICK = 4;
    static final int MAX_RECOVERY_PER_TICK = 8;

    // Passive swim-speed growth via NeoForgeMod.SWIM_SPEED (confirmed against the decompiled
    // source -- NeoForge's own purpose-built modding attribute for swim speed, included in
    // LivingEntity#createLivingAttributes for every player), applied as a flat ADD_VALUE bonus
    // scaling to max level. Flagged tunable, like every other growth constant here.
    static final double MAX_SWIM_SPEED_BONUS = 0.6;

    // Same HEALTH_TICK_COUNT-style modulo interval HungerListener ports from FoodData#tick for its
    // own starvation damage, reused here for the custom drowning-damage port.
    static final int DROWN_DAMAGE_TICK_INTERVAL = 20;

    private AirConstants() {
    }
}
