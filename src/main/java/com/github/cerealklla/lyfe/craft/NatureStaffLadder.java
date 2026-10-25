package com.github.cerealklla.lyfe.craft;

import java.util.List;
import java.util.Map;

import com.github.cerealklla.lyfe.LyfeMod;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ToolMaterial;

/**
 * The 5-tier Nature Staff ladder (design doc Section 24, explicit user spec: "5 Tiers of Nature
 * Staff, mirroring other weapons in crafting, durability, and complexity of crafting recipes...
 * melee stats similar to an Axe"). Deliberately its own small class, not folded into {@link
 * EquipmentTierLadder}'s {@code ToolType} enum -- that enum's {@link EquipmentTierLadder#itemId}
 * resolves a *real vanilla* item id by string-concatenating a material prefix (e.g.
 * {@code "iron_sword"}), which has nothing to generate for a staff (no vanilla
 * {@code iron_nature_staff} exists). Nature Staff needs 5 brand-new custom {@code Item}s instead,
 * registered in {@code registration.ModItems}.
 *
 * <p>Mapped onto the ladder's own Tiers 1-5 (Stone/Copper/Iron/Gold/Diamond) -- Tier 0 (Wood) and
 * Tier 6 (Netherite/Legendary) are skipped entirely, matching the spec's own "5 Tiers" count
 * exactly (not the full 0-6 ladder every {@code ToolType} entry spans). Melee stats are copied
 * directly from vanilla's own {@code AxeItem} registration at the matching material (confirmed
 * against the decompiled {@code Items.java} rather than guessed), so "similar to an Axe" is exact,
 * not approximated -- durability uses the same real {@link ToolMaterial#durability()} value (with
 * {@link EquipmentTierLadder#baseDurabilityOverride} still applying generically at craft time,
 * same as every other tool/weapon, since Gold's real 32 is a known outlier on this ladder's scale).
 *
 * <p>Mana/Mana Regen bonus is linear across the 5 tiers (T1 +10/+1 through T5 +50/+5, explicit
 * spec), baked directly into each tier's own default {@code ATTRIBUTE_MODIFIERS} component at
 * registration time (vanilla's own mainhand-equip mechanism applies/removes it automatically, same
 * as a sword's Attack Damage bonus -- no tick listener needed). Crafting Quality further scales
 * this bonus at craft time, mirrored onto the Mana bonus the same way {@code QualityApplier}
 * already scales Durability -- see {@code CraftingStructureMenu#attemptCraft}'s own Nature-Staff
 * branch.
 *
 * <p><b>Deliberately NOT wired into</b> (flagged scope cuts, not oversights, matching the identical
 * precedent already set by Bow/Crossbow -- see {@code EquipmentTierLadder}'s own doc): {@code
 * research.ResearchNoteConstants} (no Research Note alternate-unlock path), {@code
 * research.PassiveDurabilityResearchListener} (no passive-research-on-hit), {@code
 * craft.EquipmentGateListener}/{@code client.EquipmentSkillWarningOverlay} (no skill-level
 * wield-gating -- Bo governs XP, not a wield gate). All three are real, independently-addable
 * follow-ups, not required for the feature to function -- crafting, researching (via the Research
 * Bench's own generic "destroy an item to learn it" flow, which needs no per-type wiring at all),
 * and casting all work without them.
 *
 * <p><b>{@code structure.ShopSeedCatalogs} was initially missed here too, and that one actually was
 * a real bug</b> (fixed 2026-10-21, real report: a Blacksmith plot never listed/auto-crafted Nature
 * Staff at any tier, regardless of plot age or crafting-station tier) -- unlike the three
 * deliberate cuts above, a shop's recommended-listing catalog is NOT optional for a new equipment
 * family: {@code zone.ShopSeeding}'s Planned Inventory targets are entirely catalog-sourced, with
 * no fallback that derives a target from "every recipe a sufficiently-tiered structure could make"
 * -- skip the catalog and an NPC-owned plot can never buy/sell/auto-craft the item at all, no
 * matter how real its recipe/structure-tier support already is. See {@code ShopSeedCatalogs
 * #blacksmithListings}'s own Nature Staff loop, added alongside its Bow/Crossbow one. **Whenever a
 * new weapon/tool/armor family is added outside the {@code ToolType}/{@code ArmorType} enums,
 * check every relevant {@code ShopSeedCatalogs} function in the same pass** -- a plain new
 * {@code ToolType}/{@code ArmorType} enum member needs no such check, since those catalogs already
 * loop `ToolType.values()`/`ARMOR_TYPES` generically.
 */
public final class NatureStaffLadder {

    public static final int MIN_TIER = 1;
    public static final int MAX_TIER = 5;

    private record TierSpec(ToolMaterial material, float attackDamageBaseline, float attackSpeedBaseline) {
    }

    // Attack damage/speed baselines copied verbatim from vanilla's own AxeItem registrations
    // (Items.java): STONE_AXE/COPPER_AXE (7.0F, -3.2F), IRON_AXE (6.0F, -3.1F), GOLDEN_AXE
    // (6.0F, -3.0F), DIAMOND_AXE (5.0F, -3.0F).
    private static final Map<Integer, TierSpec> TIER_SPECS = Map.of(
            1, new TierSpec(ToolMaterial.STONE, 7.0F, -3.2F),
            2, new TierSpec(ToolMaterial.COPPER, 7.0F, -3.2F),
            3, new TierSpec(ToolMaterial.IRON, 6.0F, -3.1F),
            4, new TierSpec(ToolMaterial.GOLD, 6.0F, -3.0F),
            5, new TierSpec(ToolMaterial.DIAMOND, 5.0F, -3.0F)
    );

    private static final Map<Integer, String> TIER_DISPLAY_NAMES = Map.of(
            1, "Stone", 2, "Copper", 3, "Iron", 4, "Gold", 5, "Diamond"
    );

    private NatureStaffLadder() {
    }

    public static Identifier itemId(int tier) {
        requireValidTier(tier);
        return Identifier.fromNamespaceAndPath(LyfeMod.MODID, "nature_staff_t" + tier);
    }

    public static Identifier previousTierItem(int tier) {
        requireValidTier(tier);
        return tier == MIN_TIER ? null : itemId(tier - 1);
    }

    public static List<Identifier> allIds() {
        return List.of(itemId(1), itemId(2), itemId(3), itemId(4), itemId(5));
    }

    public static int tierOfItem(Identifier itemId) {
        for (int tier = MIN_TIER; tier <= MAX_TIER; tier++) {
            if (itemId(tier).equals(itemId)) {
                return tier;
            }
        }
        return -1;
    }

    public static ToolMaterial material(int tier) {
        requireValidTier(tier);
        return TIER_SPECS.get(tier).material();
    }

    public static float attackDamageBaseline(int tier) {
        requireValidTier(tier);
        return TIER_SPECS.get(tier).attackDamageBaseline();
    }

    public static float attackSpeedBaseline(int tier) {
        requireValidTier(tier);
        return TIER_SPECS.get(tier).attackSpeedBaseline();
    }

    public static String tierDisplayName(int tier) {
        requireValidTier(tier);
        return TIER_DISPLAY_NAMES.get(tier);
    }

    /** Max Mana bonus granted while held in the mainhand -- linear, T1=10 through T5=50. */
    public static int maxManaBonus(int tier) {
        requireValidTier(tier);
        return tier * 10;
    }

    /**
     * Mana Regen bonus granted while held in the mainhand -- linear, T1=0.4 through T5=2.0 (was a
     * flat T1=1 through T5=5 whole-number scale; cut 60%, 2026-10-24, explicit user spec: "Health
     * Regen and Mana Regen across all mods is just too high... reduce it all by 60% everywhere").
     * Fractional on purpose -- rounding this to a whole number first would zero out T1 entirely.
     */
    public static double manaRegenBonus(int tier) {
        requireValidTier(tier);
        return tier * 0.4;
    }

    private static void requireValidTier(int tier) {
        if (tier < MIN_TIER || tier > MAX_TIER) {
            throw new IllegalArgumentException("Nature Staff tier " + tier + " out of range [" + MIN_TIER + "," + MAX_TIER + "]");
        }
    }
}
