package com.github.cerealklla.lyfe.craft;

import java.util.List;
import java.util.Map;

import net.minecraft.resources.Identifier;

/**
 * Named "generic component" groups (design doc Section 19.4, added 2026-10-03 per explicit user
 * request) -- each group lists concrete, interchangeable item ids that can equally satisfy a
 * {@code GeneratedRecipe#genericComponents} requirement, e.g. "Any Log" x3 is satisfiable by any
 * mix of wood types. Deliberately NOT used for tiered variants (Cobblestone vs. Deepslate
 * Cobblestone) -- those represent a real power-level difference and stay specific, per the user's
 * own distinction. Membership confirmed against the real decompiled {@code Blocks.java} ids (no
 * guessing), and filtered the same way {@code MaterialPool} already is: stripped-log variants are
 * excluded (require an extra axe-strip action, not raw scavenging), and the Nether-exclusive
 * crimson/warped set is excluded (would break Tier 0's "any biome" accessibility rule).
 */
public final class ComponentGroups {

    static final String ANY_LOG = "Any Log";
    static final String ANY_PLANK = "Any Plank";

    private static final Map<String, List<Identifier>> GROUPS = Map.of(
            ANY_LOG, idList("oak_log", "birch_log", "spruce_log", "jungle_log", "acacia_log",
                    "dark_oak_log", "mangrove_log", "cherry_log", "pale_oak_log"),
            ANY_PLANK, idList("oak_planks", "birch_planks", "spruce_planks", "jungle_planks",
                    "acacia_planks", "dark_oak_planks", "mangrove_planks", "cherry_planks",
                    "pale_oak_planks", "bamboo_planks")
    );

    private ComponentGroups() {
    }

    private static List<Identifier> idList(String... names) {
        return List.of(names).stream().map(Identifier::withDefaultNamespace).toList();
    }

    /** Exposed publicly 2026-10-09 so {@code api.Lyfe#componentGroupMembers} can forward to it --
     * see that method's own doc for why (Settlemynts' NPC plot crafting needs to resolve generic
     * group membership without a live player/menu involved). */
    public static List<Identifier> membersOf(String groupName) {
        return GROUPS.getOrDefault(groupName, List.of());
    }
}
