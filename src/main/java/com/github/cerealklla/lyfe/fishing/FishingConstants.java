package com.github.cerealklla.lyfe.fishing;

import java.util.Map;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Fisherman skill tuning (design doc, 2026-10-03 user request) -- every placeholder magnitude here
 * is flagged the same way every other skill's own constants file flags theirs: a tunable guess, not
 * a researched-and-confirmed number.
 */
public final class FishingConstants {

    private FishingConstants() {
    }

    /** Per-species (minLb, maxLb) weight range -- mid-session correction from an original "length in inches" spec. */
    public record WeightRange(double minLb, double maxLb) {
    }

    public static final Map<Item, WeightRange> WEIGHT_RANGES = Map.of(
            Items.COD, new WeightRange(2.00, 15.00),
            Items.SALMON, new WeightRange(3.00, 30.00),
            Items.TROPICAL_FISH, new WeightRange(0.05, 1.50),
            Items.PUFFERFISH, new WeightRange(0.50, 5.00)
    );

    // Bonus-loot roll: one roll per catch (not per item), 0% at level 1 scaling linearly to this cap
    // at max level.
    public static final double MAX_BONUS_LOOT_CHANCE = 0.60;
    public static final int MIN_BONUS_ITEMS = 2;
    public static final int MAX_BONUS_ITEMS = 4;

    // Any bonus-loot slot has this flat chance of being a Research Note instead of a pool item.
    public static final double RESEARCH_NOTE_SLOT_CHANCE = 0.25;

    // Lucky Spot (D): flat additive bump to the bonus-loot roll, and the fraction of a species'
    // weight range reserved as the "upper band" a lucky-spot catch rolls from instead of the full range.
    public static final double LUCKY_SPOT_BONUS_LOOT_BUMP = 0.15;
    public static final double LUCKY_SPOT_UPPER_BAND = 0.25;
    public static final int LUCKY_SPOT_RADIUS = 10;

    // Sunken Treasure Bag (E): 5% base, +3% every 5 levels past level 30.
    public static final double SUNKEN_TREASURE_BASE_CHANCE = 0.05;
    public static final int SUNKEN_TREASURE_MIN_LEVEL = 30;
    public static final int SUNKEN_TREASURE_LEVEL_STEP = 5;
    public static final double SUNKEN_TREASURE_STEP_BONUS = 0.03;
    public static final int SUNKEN_TREASURE_MIN_GOLD_NUGGETS = 100;
    public static final int SUNKEN_TREASURE_MAX_GOLD_NUGGETS = 400;
    public static final int SUNKEN_TREASURE_MIN_NOTE_STACKS = 2;
    public static final int SUNKEN_TREASURE_MAX_NOTE_STACKS = 4;
    public static final int SUNKEN_TREASURE_MIN_NOTE_COUNT = 10;
    public static final int SUNKEN_TREASURE_MAX_NOTE_COUNT = 40;
    public static final int SUNKEN_TREASURE_MIN_HIGH_END = 1;
    public static final int SUNKEN_TREASURE_MAX_HIGH_END = 2;

    // Locked Chest / Key (G): base 1% while fishing, bumped to 10% near a shipwreck.
    public static final double LOCKED_CHEST_BASE_CHANCE = 0.01;
    public static final double LOCKED_CHEST_SHIPWRECK_CHANCE = 0.10;
    public static final int SHIPWRECK_SEARCH_RADIUS = 32;
    public static final double KEY_UNLOCK_CHANCE = 0.50;
    public static final int LOCKED_CHEST_MIN_GOLD_NUGGETS = 500;
    public static final int LOCKED_CHEST_MAX_GOLD_NUGGETS = 1000;
    public static final int LOCKED_CHEST_NOTE_STACKS = 6;
    public static final int LOCKED_CHEST_MIN_NOTE_COUNT = 20;
    public static final int LOCKED_CHEST_MAX_NOTE_COUNT = 50;
    public static final int LOCKED_CHEST_MIN_HIGH_END = 4;
    public static final int LOCKED_CHEST_MAX_HIGH_END = 6;

    public static final String[] CHEST_VARIANTS = {"granite", "basalt", "slate", "quartzite", "obsidian"};

    // Catch Bag (H): total weight cap, not an item/slot count.
    public static final double CATCH_BAG_MAX_POUNDS = 1000.0;

    // Base XP per catch, plus a small per-pound bonus so a heavier fish feels marginally more valuable.
    public static final long BASE_CATCH_XP = 5L;
    public static final double XP_PER_POUND = 0.5;

    // Nether/End custom catch loop (F) -- mirrors vanilla's own 100-600 tick lure-wait range.
    public static final int VOID_CATCH_MIN_TICKS = 100;
    public static final int VOID_CATCH_MAX_TICKS = 600;

    // Deep-water gate for Sunken Treasure -- how many contiguous water blocks below the bobber count as "deep."
    public static final int DEEP_WATER_MIN_COLUMN = 4;
    public static final int DEEP_WATER_SCAN_CAP = 8;
}
