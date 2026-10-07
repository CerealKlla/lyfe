package com.github.cerealklla.lyfe.craft;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.github.cerealklla.lyfe.skill.SkillId;
import com.github.cerealklla.lyfe.skill.Skills;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.equipment.ArmorType;

/**
 * The material tier ladder for Slice 1 of the crafting overhaul (design doc Section 19.4): reuses
 * vanilla's own tool-material progression directly rather than inventing a new material-power
 * score. Wood/Tier 0 joined the generated-recipe system 2026-10-03 (previously excluded, "known by
 * every player by default") -- it still stays auto-known (see {@code
 * craft.CraftingStructureMenu#computeCraftable}/{@code research.ResearchMenu}'s Tier-0 guards), but
 * now gets a real randomized-but-cheap recipe instead of vanilla's fixed one, same as every other
 * tier. Wood/Stone/Copper/Iron/Gold/Diamond/Netherite map onto Tiers 0-6. Armor's own Tier 0
 * (Leather, added 2026-10-04) is auto-known the same way, for the same reason -- confirmed with the
 * user 2026-10-05 after a brief reversal (see decisions.md).
 *
 * <p>Copper inserted 2026-10-03 (explicit user request, confirmed against the real decompiled
 * {@code ToolMaterial.java}: Copper genuinely sits between Stone and Iron in this environment's
 * vanilla build -- durability 190 vs. Stone's 131 and Iron's 250, same +1.0 attack bonus as Stone)
 * at Tier 2, pushing Iron/Gold/Diamond up one slot each. The former top tier (Netherite) is now
 * reframed as "Legendary" and kept as a soft/placeholder Tier 6 -- {@link #MAX_TIER} includes it (so
 * the data model/recipe generation/vanilla-recipe-stripping all stay consistent), but {@link
 * #MAX_REACHABLE_TIER} deliberately excludes it from every real player-facing path this pass (no
 * crafting-structure block reaches it, {@code CrafterConstants#researchUnlockedTier} doesn't gate
 * up to it, {@code research.ResearchNoteLootInjector} never drops a note for it) -- confirmed with
 * the user ("6th tier is unreachable right now"); a future slice can open it up.
 *
 * <p>Tools/weapons: Sword/Pickaxe/Axe/Shovel/Hoe/Spear, Spear added 2026-10-03. Armor (Helmet/
 * Chestplate/Leggings/Boots, added 2026-10-04 -- see decisions.md) reuses vanilla's own {@link
 * ArmorType} directly rather than a parallel enum, and its own tier ladder is
 * Leather/Chainmail/Copper/Iron/Gold/Diamond/Netherite -- the same 7-tier shape as tools/weapons,
 * confirmed with the user to give armor its own real Copper rung (vanilla has one in this
 * environment) rather than skipping straight from Chainmail to Iron. Horse armor (3 real tiers:
 * Iron/Gold/Diamond, no Leather/Chainmail/Copper/Netherite equivalents) is explicitly OUT of scope
 * this pass -- a genuinely different tier shape, flagged for a future slice, not an oversight.
 *
 * <p>Slot-capacity-per-tier mirrors Section 19.6's crafting-structure grid sizes (2x2/3x3/5x3/6x4/
 * 7x5), reinterpreted as an unordered ingredient-slot *count* rather than a spatial layout --
 * confirmed with the user 2026-10-02 that no 2D arrangement is needed at all.
 */
public final class EquipmentTierLadder {

    public enum ToolType {
        // SPEAR added 2026-10-03 (user request) -- confirmed every tier has a real matching
        // "<material>_spear" vanilla item (same naming convention as every other tool here), so it
        // slots straight into the existing generic ToolType.values() loops everywhere (generation,
        // vanilla-recipe-stripping, Research Note loot injection) with no special-casing needed.
        SWORD, PICKAXE, AXE, SHOVEL, HOE, SPEAR
    }

    public static final int MIN_TIER = 0;
    public static final int MAX_TIER = 6;

    // The highest tier obtainable through any CURRENT normal player path (crafting at a real
    // station, researching via normal Crafter leveling, or finding a Research Note) -- see the
    // class doc above. Legendary (MAX_TIER) is deliberately excluded.
    public static final int MAX_REACHABLE_TIER = 5;

    // "gold" is the material's own name (gold_ingot, gold_block, ...), but vanilla's real
    // tool/weapon item ids use the adjective "golden_" instead (golden_sword, golden_pickaxe, ...) --
    // confirmed against the decompiled Items.java (2026-10-02, found via a live playtest report:
    // "golden_sword" is a real item, "gold_sword" is not, so this tier was silently broken end to
    // end -- unresearchable since the item could never be held, and vanilla's real recipe was never
    // actually stripped since the removal id was wrong too). Every other material name here matches
    // vanilla's own item-id prefix exactly. "wooden" added 2026-10-03 at index 0 for Tier 0;
    // "copper" added 2026-10-03 at index 2 for Tier 2 (see class doc).
    private static final List<String> TIER_ITEM_PREFIXES =
            List.of("wooden", "stone", "copper", "iron", "golden", "diamond", "netherite");

    // Armor's own material-name order (2026-10-04) -- Leather/Chainmail stand in for Wood/Stone
    // (armor has no wood-tier equivalent at all), then Copper/Iron/Golden/Diamond/Netherite match
    // the tool ladder's own prefixes exactly from Tier 2 up. "golden_helmet" etc. confirmed real
    // (same "golden" adjective as tools, not "gold").
    private static final List<String> ARMOR_TIER_PREFIXES =
            List.of("leather", "chainmail", "copper", "iron", "golden", "diamond", "netherite");

    // The 4 real humanoid armor slots this ladder covers -- ArmorType.BODY (horse/wolf/etc. armor)
    // is deliberately excluded, see the class doc above.
    public static final List<ArmorType> ARMOR_TYPES = List.of(ArmorType.HELMET, ArmorType.CHESTPLATE, ArmorType.LEGGINGS, ArmorType.BOOTS);

    // Custom base-durability overrides per tier (2026-10-06, explicit user request), applied by
    // CraftingStructureMenu#attemptCraft before QualityApplier's own % bonus so Quality always
    // boosts off the *custom* base, not vanilla's raw ToolMaterial durability. Only tiers listed
    // here deviate from vanilla's own value (see decisions.md for the full vanilla table this is
    // based on: Wood 59, Stone 131, Copper 190, Iron 250, Gold 32, Diamond 1561, Netherite 2031) --
    // every tier not in this map keeps using {@code stack.getMaxDamage()}'s existing vanilla value
    // unmodified.
    //
    // Tier 0 (Wood) raised 59 -> 200 flat per explicit request -- deliberately breaks strict
    // ascending order against Stone/Copper (a newbie-tier buff, not a progression rung).
    //
    // Tier 4 (Gold) raised 32 -> 900 ("fall where appropriate on the custom scale," user's own
    // wording) -- vanilla's real Gold durability is an outlier (fast-but-fragile, unrelated to this
    // ladder's own ordering), so it's repositioned as the linear midpoint between Tier 3 (Iron, 250)
    // and Tier 5 (Diamond, 1561) -- (250 + 1561) / 2 ~= 900 -- restoring a monotonically increasing
    // scale from Tier 1 up. Flagged as a judgment call, easy to retune.
    private static final Map<Integer, Integer> BASE_DURABILITY_OVERRIDE_BY_TIER = Map.of(
            0, 200,
            4, 900
    );

    private EquipmentTierLadder() {
    }

    /**
     * The custom base max-damage override for {@code tier}, or {@code -1} if this tier keeps
     * vanilla's own {@code ToolMaterial} durability unmodified. Applies uniformly to every
     * {@link ToolType} at that tier -- vanilla's own durability is already uniform per material
     * regardless of tool type (confirmed against the decompiled {@code ToolMaterial.java}), so this
     * override follows the same shape.
     */
    public static int baseDurabilityOverride(int tier) {
        return BASE_DURABILITY_OVERRIDE_BY_TIER.getOrDefault(tier, -1);
    }

    // Which skill governs "is the player's level high enough to wield this tool/weapon" gating
    // (2026-10-06 user request) -- consumed by both the held-item red-caution HUD and the
    // equip-blocking check (see craft.EquipmentGateListener). Axe is deliberately mapped to
    // Axeman here, NOT Lumberjack -- the user's own explicit split between the combat-context gate
    // (this one) and Lumberjack's pre-existing gathering-context speed gate
    // (gathering.GatheringListener/ToolTierUnlocks), which stays completely untouched.
    private static final Map<ToolType, SkillId> GOVERNING_SKILL_BY_TOOL_TYPE = Map.of(
            ToolType.SWORD, Skills.SWORDSMAN_ID,
            ToolType.PICKAXE, Skills.MINER_ID,
            ToolType.AXE, Skills.AXEMAN_ID,
            ToolType.SHOVEL, Skills.EXCAVATOR_ID,
            ToolType.HOE, Skills.FARMER_ID,
            ToolType.SPEAR, Skills.PIKEMAN_ID
    );

    public static SkillId governingSkillId(ToolType tool) {
        return GOVERNING_SKILL_BY_TOOL_TYPE.get(tool);
    }

    private static final List<String> TIER_DISPLAY_NAMES =
            List.of("Wood", "Stone", "Copper", "Iron", "Gold", "Diamond", "Netherite");

    /** Human-readable name for {@code tier}, e.g. {@code "Copper"} -- used by Skills-screen benefit lines. */
    public static String tierDisplayName(int tier) {
        requireValidTier(tier);
        return TIER_DISPLAY_NAMES.get(tier);
    }

    /** The real vanilla item id for {@code tool} at {@code tier} (e.g. {@code minecraft:iron_sword}, {@code minecraft:golden_axe}). */
    public static Identifier itemId(ToolType tool, int tier) {
        requireValidTier(tier);
        String material = TIER_ITEM_PREFIXES.get(tier);
        return Identifier.withDefaultNamespace(material + "_" + tool.name().toLowerCase(Locale.ROOT));
    }

    /** The real vanilla item id for {@code armorType} at {@code tier} (e.g. {@code minecraft:iron_helmet}). */
    public static Identifier armorItemId(ArmorType armorType, int tier) {
        requireValidTier(tier);
        String material = ARMOR_TIER_PREFIXES.get(tier);
        return Identifier.withDefaultNamespace(material + "_" + armorType.getName());
    }

    /**
     * Which of this ladder's 4 humanoid {@link ArmorType}s {@code itemId} is, or {@code null} if
     * it isn't an armor item on this ladder at all (a tool/weapon id, or something else entirely).
     * Used at craft time ({@code CraftingStructureMenu#attemptCraft}) to apply {@code
     * ArmorRebalance}'s rebalanced defense/toughness to a freshly-crafted armor piece.
     */
    public static ArmorType armorTypeOf(Identifier itemId) {
        String path = itemId.getPath();
        for (ArmorType armorType : ARMOR_TYPES) {
            if (path.endsWith("_" + armorType.getName())) {
                return armorType;
            }
        }
        return null;
    }

    /**
     * The tier a real held {@code item} represents for {@code tool} (e.g. an Iron Axe is Tier 3),
     * or {@code -1} if it isn't one of this ladder's own items at all (a modded tool, or the wrong
     * {@link ToolType}). Used by {@code gathering.GatheringListener}'s Section 7 tool-tier gate
     * (2026-10-04) to find which rung a held Axe/Pickaxe sits on.
     */
    public static int tierOfItem(ToolType tool, Item item) {
        Identifier itemKey = BuiltInRegistries.ITEM.getKey(item);
        for (int tier = MIN_TIER; tier <= MAX_TIER; tier++) {
            if (itemId(tool, tier).equals(itemKey)) {
                return tier;
            }
        }
        return -1;
    }

    /**
     * The armor tier a real held {@code item} represents for {@code armorType} (e.g. a Diamond
     * Helmet is Tier 5), or {@code -1} if it isn't one of this ladder's own armor items at all.
     * Mirrors {@link #tierOfItem}; used by {@code EquipmentGateListener}'s 2026-10-06 armor-equip
     * block.
     */
    public static int tierOfArmorItem(ArmorType armorType, Item item) {
        Identifier itemKey = BuiltInRegistries.ITEM.getKey(item);
        for (int tier = MIN_TIER; tier <= MAX_TIER; tier++) {
            if (armorItemId(armorType, tier).equals(itemKey)) {
                return tier;
            }
        }
        return -1;
    }

    /**
     * Section 19.6's grid sizes, reinterpreted as a plain ingredient-slot count. A generated recipe
     * uses between 50% and 100% of this amount (minus 1 reserved slot for the previous-tier upgrade
     * component at Tier 1+, see {@code RecipeGenerator}). Tier 0 (Wood) is deliberately smaller than
     * Tier 1 -- "incredibly cheap" per the user's own spec. Tier 2 (Copper, 6) is a new interpolated
     * value between Stone's 4 and Iron's 9; every other tier keeps its exact previously-tuned value,
     * just shifted up one index to match its new tier number.
     */
    public static int slotCapacity(int tier) {
        requireValidTier(tier);
        return switch (tier) {
            case 0 -> 3;
            case 1 -> 4;
            case 2 -> 6;
            case 3 -> 9;
            case 4 -> 15;
            case 5 -> 24;
            case 6 -> 35;
            default -> throw new IllegalArgumentException("tier " + tier);
        };
    }

    /** Every item id this slice generates a recipe for (6 tools x Tiers 0-6 = 42 items), including Wood (auto-known, see {@code CraftingStructureMenu}/{@code ResearchMenu}) and the dormant Legendary tier. */
    public static List<Identifier> allGeneratedItemIds() {
        List<Identifier> ids = new java.util.ArrayList<>();
        for (ToolType tool : ToolType.values()) {
            for (int tier = MIN_TIER; tier <= MAX_TIER; tier++) {
                ids.add(itemId(tool, tier));
            }
        }
        return List.copyOf(ids);
    }

    /**
     * The representative item shown floating above a crafting structure of {@code structureTier}
     * (design doc, 2026-10-05 user request: "I'd like to see an item floating over top of them which
     * indicates what kind of structure it is... e.g. the Engineers Table would have a Sword floating
     * over top of it"). Resolved as "the highest-tier item this structure unlocks" -- a crafting
     * structure's own numeric tier is exactly the highest equipment tier it can craft (see {@code
     * craft.CraftingStructureMenu#computeCraftable}'s {@code r.tier() <= tier} filter), so this is
     * simply that tier's own Sword. Confirmed against the user's own example: Engineer's Bench is
     * Tier 5, {@link #TIER_ITEM_PREFIXES}.get(5) is "diamond" -- a Diamond Sword, matching exactly.
     */
    public static Identifier structureIconItemId(int structureTier) {
        return itemId(ToolType.SWORD, structureTier);
    }

    /** Every armor item id this slice generates a recipe for (4 slots x Tiers 0-6 = 28 items), added 2026-10-04. */
    public static List<Identifier> allGeneratedArmorItemIds() {
        List<Identifier> ids = new java.util.ArrayList<>();
        for (ArmorType armorType : ARMOR_TYPES) {
            for (int tier = MIN_TIER; tier <= MAX_TIER; tier++) {
                ids.add(armorItemId(armorType, tier));
            }
        }
        return List.copyOf(ids);
    }

    /**
     * Purely a visual slot-grid layout for the crafting-structure UI (Section 19.6) -- no
     * recipe-matching meaning. Keyed to the crafting-STRUCTURE tier (1-5, one physical block per
     * rung, see {@code CraftingStructureBlockEntity}), a separate axis from the equipment tier this
     * class otherwise represents -- it only ever gets called with a real structure's tier, never 6.
     */
    public static int[] gridDims(int tier) {
        requireValidTier(tier);
        return switch (tier) {
            case 1 -> new int[]{2, 2};
            case 2 -> new int[]{3, 3};
            case 3 -> new int[]{5, 3};
            case 4 -> new int[]{6, 4};
            case 5 -> new int[]{7, 5};
            default -> throw new IllegalArgumentException("tier " + tier);
        };
    }

    private static void requireValidTier(int tier) {
        if (tier < MIN_TIER || tier > MAX_TIER) {
            throw new IllegalArgumentException("tier " + tier + " out of range [" + MIN_TIER + "," + MAX_TIER + "]");
        }
    }
}
