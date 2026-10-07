package com.github.cerealklla.lyfe.craft;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.resources.Identifier;

/**
 * A curated, hand-maintained "which real items represent roughly this power tier" pool used only
 * to weight random recipe generation (design doc Section 19.4) -- NOT an exhaustive catalog of
 * every item in the game, and explicitly a tunable first pass like every other placeholder number
 * in this mod.
 *
 * <p>Restructured 2026-10-03 (explicit user request) into three cumulative-vs-exclusive categories,
 * matching vanilla's own real tool-tier mining gates (stone pickaxe for iron/copper/lapis, iron
 * pickaxe for gold/diamond/emerald/redstone, diamond pickaxe for obsidian/ancient debris) where one
 * existed:
 * <ul>
 *   <li><b>Generic items</b> ({@link #TIER_GENERIC_ITEMS}) -- common, single-item, "scavenge it in
 *   any biome" filler with no flavor variants worth grouping (a stick is just a stick).</li>
 *   <li><b>Generic groups</b> ({@link #TIER_GENERIC_GROUPS}) -- same "common" tier, but backed by a
 *   named {@link ComponentGroups} group instead of one hardcoded item, so e.g. a Wood recipe's log
 *   requirement is satisfiable by whichever wood type is actually nearby.</li>
 *   <li><b>Specialized</b> ({@link #TIER_SPECIALIZED}) -- the tier's own distinctive flavor items.
 *   For every REACHABLE tier (1-5), exactly {@code tier} many of them (Tier 1 has 1, ... Tier 5 has
 *   5) -- the user's explicit limit. Tier 6 (Legendary, a soft/placeholder tier, see
 *   {@code EquipmentTierLadder}'s class doc) deliberately stays at its pre-Copper-insertion count of
 *   5 rather than growing to 6, since that "5 specialized max" was stated as an absolute cap, not a
 *   coincidence of the old numbering. None of these are inherited by higher tiers -- only reachable
 *   at a higher tier through {@code RecipeGenerator#rollWeightedTier}'s own cross-tier weighting,
 *   same as before.</li>
 * </ul>
 * Generic items and generic groups are both strictly additive tier-over-tier and share one combined
 * budget, capped at 10 unique catalog entries total (a group counts as a single entry regardless of
 * its member count) per the user's explicit limit -- already reached at Tier 1. Tier 6 also carries
 * exactly one "boss-drop quality" item ({@code dragon_breath}, from the Ender Dragon) per the user's
 * "maybe 1 boss-drop quality resource" allowance -- {@code nether_star} (a Wither drop) already
 * filled that role before the generic/specialized restructure, so this treats a genuine raid/boss
 * kill as the ceiling of exoticism, same bar as before, just made explicit.
 */
final class MaterialPool {

    private static final Map<Integer, List<Identifier>> TIER_GENERIC_ITEMS = Map.of(
            0, idList("stick", "wheat_seeds", "string", "flint", "charcoal"),
            1, idList("cobblestone", "gravel", "coarse_dirt"),
            // Combined generic (items + groups) budget caps at 10 unique entries (user's explicit
            // limit) -- already reached below at Tier 1 (5 items + 2 groups + 3 items = 10), so
            // every tier above introduces no further generics of their own, only specialized.
            2, idList(),
            3, idList(),
            4, idList(),
            5, idList(),
            6, idList()
    );

    private static final Map<Integer, List<String>> TIER_GENERIC_GROUPS = Map.of(
            // oak_log replaced 2026-10-03 (user correction) -- a hardcoded single wood type can't
            // be satisfied in a biome where that type doesn't grow; these resolve to ANY member at
            // craft time instead (see ComponentGroups/CraftingStructureMenu).
            0, List.of(ComponentGroups.ANY_LOG, ComponentGroups.ANY_PLANK),
            1, List.of(),
            2, List.of(),
            3, List.of(),
            4, List.of(),
            5, List.of(),
            6, List.of()
    );

    // Renumbered 2026-10-03 for Copper's insertion at Tier 2 (see EquipmentTierLadder's class doc):
    // every previously-tuned list keeps its exact items, just shifted up one tier index to stay
    // attached to the same real material (old Tier 2/Iron -> new Tier 3, etc.), topped up with one
    // new item where the tier-number-count rule now requires more than before.
    private static final Map<Integer, List<Identifier>> TIER_SPECIALIZED = Map.of(
            0, idList(),
            // Granite chosen from the user's own example set (Oak Log / Granite / Bamboo) for Tier
            // 1's single specialized slot -- ordinary Overworld mining, not biome-restricted the
            // way Bamboo (jungle-only) would be.
            1, idList("granite"),
            // Copper (NEW, Tier 2): raw_copper is the ordinary-mining form, copper_ingot the
            // refined one -- same "ingot + raw/nugget" flavor pairing as every other metal tier.
            2, idList("copper_ingot", "raw_copper"),
            // raw_iron added as the 3rd item (Tier 3 now needs 3, had 2 before the renumber).
            3, idList("iron_ingot", "raw_iron", "lapis_lazuli"),
            // raw_gold added as the 4th item (Tier 4 now needs 4, had 3 before the renumber).
            4, idList("gold_ingot", "gold_nugget", "raw_gold", "amethyst_shard"),
            // crying_obsidian added as the 5th item (Tier 5 now needs 5, had 4 before the renumber)
            // -- Ruined-Portal/Piglin-barter sourced, same "more exotic allowed" bar already used
            // for obsidian/blaze_rod at this tier.
            5, idList("diamond", "emerald", "blaze_rod", "obsidian", "crying_obsidian"),
            // Legendary (soft Tier 6) intentionally stays at its old 5-item list rather than growing
            // to 6 -- see class doc.
            6, idList("netherite_scrap", "netherite_ingot", "ancient_debris", "nether_star", "dragon_breath")
    );

    private static final Map<Integer, List<MaterialPick>> PICKS = buildCumulativePicks();

    private MaterialPool() {
    }

    sealed interface MaterialPick {
        record Specific(Identifier id) implements MaterialPick {
        }

        record Generic(String groupName) implements MaterialPick {
        }
    }

    private static Map<Integer, List<MaterialPick>> buildCumulativePicks() {
        Map<Integer, List<MaterialPick>> picks = new HashMap<>();
        List<MaterialPick> genericsSoFar = new ArrayList<>();
        for (int t = 0; t <= EquipmentTierLadder.MAX_TIER; t++) {
            TIER_GENERIC_ITEMS.get(t).forEach(id -> genericsSoFar.add(new MaterialPick.Specific(id)));
            TIER_GENERIC_GROUPS.get(t).forEach(name -> genericsSoFar.add(new MaterialPick.Generic(name)));

            List<MaterialPick> combined = new ArrayList<>(genericsSoFar);
            TIER_SPECIALIZED.get(t).forEach(id -> combined.add(new MaterialPick.Specific(id)));
            picks.put(t, List.copyOf(combined));
        }
        return Map.copyOf(picks);
    }

    private static List<Identifier> idList(String... names) {
        return List.of(names).stream().map(Identifier::withDefaultNamespace).toList();
    }

    static List<MaterialPick> picksForTier(int tier) {
        return PICKS.getOrDefault(Math.max(0, Math.min(EquipmentTierLadder.MAX_TIER, tier)), PICKS.get(0));
    }
}
