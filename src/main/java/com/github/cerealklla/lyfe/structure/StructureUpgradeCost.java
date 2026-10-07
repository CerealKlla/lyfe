package com.github.cerealklla.lyfe.structure;

import java.util.List;

import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;

/**
 * The resource cost table for crafting/cooking structure tier upgrades (design doc Section 19.9,
 * added 2026-10-05, explicit user request: "200 logs of any time, 150 cobblestone", scaling per
 * tier). Shared by both {@code craft.CraftingStructureMenu} and {@code cook.CookingStructureMenu} --
 * the two ladders' upgrades cost the same thing, since nothing about the request distinguished them.
 *
 * <p><b>Placeholder values, same as every other cost/XP table in this suite</b> -- base amounts are
 * for the very first upgrade (Tier 1 -> 2); each subsequent tier scales linearly (multiplier =
 * {@code nextTier - 1}), flagged for retuning after the first live playtest, not a final balance
 * pass.
 */
public final class StructureUpgradeCost {

    private static final int BASE_LOG_AMOUNT = 200;
    private static final int BASE_COBBLESTONE_AMOUNT = 150;

    private StructureUpgradeCost() {
    }

    public static List<UpgradeCostEntry> costFor(int nextTier) {
        int multiplier = Math.max(1, nextTier - 1);
        return List.of(
                UpgradeCostEntry.ofTag(ItemTags.LOGS, BASE_LOG_AMOUNT * multiplier),
                UpgradeCostEntry.ofItem(Identifier.withDefaultNamespace("cobblestone"), BASE_COBBLESTONE_AMOUNT * multiplier)
        );
    }
}
