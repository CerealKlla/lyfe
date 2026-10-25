package com.github.cerealklla.lyfe.hunger;

/**
 * Constants for the decoupled-hunger mechanism (design doc Section 10.0). The threshold constants
 * mirror vanilla's own {@code net.minecraft.world.food.FoodConstants} values exactly (confirmed
 * against the decompiled 26.1.2.109 source) -- they stay fixed/absolute regardless of a player's
 * current max, which is what makes a bigger true-hunger max a genuine survivability reward rather
 * than a cosmetic rescale (see decisions.md, 2026-09-24).
 */
final class HungerConstants {

    static final int BASE_MAX_HUNGER = 20; // vanilla FoodConstants.MAX_FOOD
    static final int MAX_HUNGER_AT_MAX_LEVEL = 40; // up to 20 icons -- lowered from 30 icons, 2026-10-17 user request
    static final float START_SATURATION = 5.0F; // vanilla FoodConstants.START_SATURATION

    // The value vanilla's real FoodData#foodLevel is pinned to every tick, so its own sprint/regen/
    // starvation thresholds never fire off a stale value. Chosen to sit strictly between
    // SPRINT_LEVEL and HEAL_LEVEL (never triggers either), and below BASE_MAX_HUNGER (so
    // Player#needsFood() -- foodLevel < 20 -- stays true and real eating still works normally).
    static final int REAL_FOOD_PIN = 17;

    static final int SPRINT_LEVEL = 6; // vanilla FoodConstants.SPRINT_LEVEL
    static final int HEAL_LEVEL = 18; // vanilla FoodConstants.HEAL_LEVEL
    static final int STARVE_LEVEL = 0; // vanilla FoodConstants.STARVE_LEVEL
    static final int HEALTH_TICK_COUNT = 80; // vanilla FoodConstants.HEALTH_TICK_COUNT
    static final int HEALTH_TICK_COUNT_SATURATED = 10; // vanilla FoodConstants.HEALTH_TICK_COUNT_SATURATED

    // Both cut 60% (-> 0.4x), 2026-10-24 -- explicit user report: "Vanilla is healing you from
    // having a full stomach. That heal rate seems too high; I am not wearing any armor at all and
    // basically am [im]mortal." Event *frequency* (the two HEALTH_TICK_COUNT* constants above) is
    // left untouched -- only how much each regen tick actually heals. EXHAUSTION_HEAL was vanilla's
    // own FoodConstants value (6.0F); HEAL_AMOUNT replaces what vanilla hardcodes as a bare literal
    // 1.0F directly in FoodData#tick's non-saturated branch (named here instead, matching this
    // class's own "every real number gets a name" convention).
    static final float EXHAUSTION_HEAL = 2.4F; // was vanilla FoodConstants.EXHAUSTION_HEAL (6.0F)
    static final float HEAL_AMOUNT = 0.4F; // was vanilla's own hardcoded 1.0F (half a heart) per tick

    // Real report, 2026-10-19 (reported twice): "food drops at normal pace until 3 icons, then sits
    // there forever... I could sprint for hours and never die." Root cause confirmed, not a bug in
    // the pin/mirror mechanism itself -- SPRINT_LEVEL=6 (3 icons) is exactly where enforceSprintLock
    // forcibly cancels sprinting every tick, and vanilla's own exhaustion model generates ZERO
    // exhaustion from plain walking (only sprinting/jumping/combat do, confirmed vanilla behavior --
    // a real vanilla player who only walks never starves either). Once sprint is locked out, a player
    // who isn't jumping or fighting has no remaining exhaustion source at all, so true hunger
    // legitimately stops moving -- faithful to vanilla, but not what's wanted here: hunger should
    // still be a real, eventual threat just from existing. This adds a small flat exhaustion trickle
    // every tick regardless of activity, added on top of (not replacing) vanilla's own real sources --
    // deliberately slow: 0.004/tick = 0.08/sec, crossing vanilla's 4.0 threshold roughly every 50s, so
    // draining a full 40-point (20-icon) max from standing completely still takes over half an hour.
    // Placeholder magnitude, flagged tunable like every other number in this class.
    static final float PASSIVE_EXHAUSTION_PER_TICK = 0.004F;

    private HungerConstants() {
    }
}
