package com.github.cerealklla.lyfe.mayor;

import java.util.ArrayList;
import java.util.List;

/**
 * The Mayor skill's tunable numbers (design doc, added 2026-10-09, explicit spec: "Gains XP any time
 * a plot in a Settlement you are the mayor of is upgraded... The XP reward is based on the new Tier,
 * so upgrading to tier 2 is less xp than upgrading to tier 3... I want a player with just 1 settlement
 * to be able to progress through the levels decently up to about level 25/50").
 *
 * <p><b>Placeholder values, same as every other cost/XP table in this suite</b> -- a single
 * fully-grown settlement (Tier 5 Town Hall, its own plot cap of 50 plots, every plot upgraded
 * Tier 1 -> 5) yields roughly 50 * (50 + 150 + 400 + 1000) = 80,000 Mayor XP at most, close to this
 * skill's own {@code mayorCurve()} level-50 total -- so maxing out one settlement lands a Mayor
 * somewhere around level 50, with levels past 25 realistically needing most of that growth, matching
 * the "need an additional settlement past 25/50" spec. Flagged for retuning once real play confirms
 * actual settlement growth rates.
 *
 * <p>{@link #minMayorLevelForZoneType}'s schedule is this class's own judgment call (explicit user
 * instruction: "you decide what seems reasonable and then we'll tweak") -- Town Hall/Farm/Lumberjack/
 * Private Residence are always available (level 0, matching the user's own named starter set, with
 * Private Residence folded in since housing is as basic as Farm/Lumberjack); everything else unlocks
 * in rough order of how specialized/powerful it is.
 */
public final class MayorConstants {

    private MayorConstants() {
    }

    public static int xpForPlotUpgrade(int newTier) {
        return switch (newTier) {
            case 2 -> 50;
            case 3 -> 150;
            case 4 -> 400;
            case 5 -> 1000;
            default -> 0;
        };
    }

    public static int minMayorLevelForZoneType(String zoneTypePath) {
        return switch (zoneTypePath) {
            case "town_hall", "farm", "lumberyard", "private_residence" -> 0;
            case "blacksmith" -> 5;
            case "guardhouse", "grocer" -> 10;
            case "armorer", "stonemason" -> 15;
            case "building_supplier", "restaurant" -> 20;
            case "tavern" -> 25;
            case "traveling_merchant_stall" -> 30;
            default -> 0;
        };
    }

    /** {@code (unlock level, display label)} pairs, in ascending-level order -- drives {@link #benefitLines}. */
    private static final List<java.util.Map.Entry<Integer, String>> UNLOCK_SCHEDULE = List.of(
            java.util.Map.entry(5, "Blacksmith"),
            java.util.Map.entry(10, "Guardhouse, Grocer"),
            java.util.Map.entry(15, "Armorer, Stonemason"),
            java.util.Map.entry(20, "Building Supplier, Restaurant"),
            java.util.Map.entry(25, "Tavern"),
            java.util.Map.entry(30, "Traveling Merchant Stall")
    );

    /** Skills screen benefit lines (2026-10-09) -- what's unlocked so far, and the next upcoming unlock, if any. */
    public static List<String> benefitLines(int level) {
        List<String> lines = new ArrayList<>();
        lines.add("Town Hall, Farm, Lumberyard, and Private Residence are always available.");
        for (var entry : UNLOCK_SCHEDULE) {
            if (level >= entry.getKey()) {
                lines.add("Unlocked at level " + entry.getKey() + ": " + entry.getValue());
            } else {
                lines.add("Unlocks at level " + entry.getKey() + ": " + entry.getValue());
                break;
            }
        }
        return lines;
    }
}
