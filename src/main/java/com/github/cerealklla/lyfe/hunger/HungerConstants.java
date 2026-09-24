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
    static final int MAX_HUNGER_AT_MAX_LEVEL = 60; // design doc: "up to 30 icons"
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
    static final float EXHAUSTION_HEAL = 6.0F; // vanilla FoodConstants.EXHAUSTION_HEAL

    private HungerConstants() {
    }
}
