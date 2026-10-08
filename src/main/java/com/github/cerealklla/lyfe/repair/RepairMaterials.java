package com.github.cerealklla.lyfe.repair;

import java.util.List;

import com.github.cerealklla.lyfe.structure.UpgradeCostEntry;

import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * One repair material per {@code craft.EquipmentTierLadder} tier (2026-10-07 user spec, given
 * directly rather than derived from each item's own crafting recipe -- a deliberately simpler,
 * separate cost table): a full repair (0% durability remaining, restored to 100%) costs {@link
 * #FULL_REPAIR_AMOUNT} units of that tier's material; {@code RepairCost#costFor} scales this down
 * proportionally to how much durability is actually missing.
 *
 * <p>Tiers 0-1 split by tool vs. armor (real bug found live, 2026-10-07: a Leather Helmet was
 * costing Planks to repair) -- {@code EquipmentTierLadder.ARMOR_TIER_PREFIXES} uses Leather/Chainmail
 * at tiers 0/1 where the tool ladder uses Wood/Stone, so those two tiers need their own armor-specific
 * entry. Tiers 2-6 are already identical between the two ladders (Copper/Iron/Gold/Diamond/Netherite),
 * so one shared entry covers both. Tier 1 (Chainmail) has no real vanilla "chainmail" crafting
 * material at all -- Iron Ingot used as a stand-in per the user's own choice (chainmail being a
 * woven-iron product, and Iron is already the very next real tier up).
 *
 * <p>Tier 0 tool material uses vanilla's real {@code ItemTags#PLANKS} tag rather than one specific
 * plank variant -- any plank works, matching this tag's existing use as "generic wood" everywhere
 * else a real vanilla tag exists for it (see {@code structure.UpgradeCostEntry}'s own tag-or-item
 * shape, which this reuses directly since it's also what a Settlemynts/Yconomics Shop purchase needs
 * to be priced/matched against). Every other tier is one specific vanilla ingredient.
 *
 * <p><b>Gold tier (4) assumed, not given explicitly</b>: the user's own list named Wood/Stone(
 * Cobblestone)/Copper/Iron/Diamond/Netherite but skipped Gold -- filled in here as 9 Gold Ingots,
 * following the exact same pattern as every other tier. Easy to correct if that assumption is wrong.
 */
final class RepairMaterials {

    static final int FULL_REPAIR_AMOUNT = 9;

    private static final UpgradeCostEntry TOOL_TIER_0 = UpgradeCostEntry.ofTag(ItemTags.PLANKS, FULL_REPAIR_AMOUNT);
    private static final UpgradeCostEntry TOOL_TIER_1 = UpgradeCostEntry.ofItem(Identifier.withDefaultNamespace("cobblestone"), FULL_REPAIR_AMOUNT);
    private static final UpgradeCostEntry ARMOR_TIER_0 = UpgradeCostEntry.ofItem(Identifier.withDefaultNamespace("leather"), FULL_REPAIR_AMOUNT);
    private static final UpgradeCostEntry ARMOR_TIER_1 = UpgradeCostEntry.ofItem(Identifier.withDefaultNamespace("iron_ingot"), FULL_REPAIR_AMOUNT);

    // Every damageable vanilla item outside the ladder entirely (shield, bow, trident, elytra, ...) --
    // no tier concept exists for these at all, so one flat material covers all of them, same Iron
    // Ingot already used as Chainmail's own stand-in material.
    private static final UpgradeCostEntry GENERIC = UpgradeCostEntry.ofItem(Identifier.withDefaultNamespace("iron_ingot"), FULL_REPAIR_AMOUNT);

    // Fishing Rod carved out of GENERIC, 2026-10-08 (explicit user request: "should be repaired with
    // sticks, not iron ingots") -- a real vanilla Fishing Rod is itself mostly Stick (3 of its own 4
    // crafting-recipe slots), so Iron Ingot never made sense for it specifically, just happened to be
    // whatever every other off-ladder item defaulted to.
    private static final UpgradeCostEntry FISHING_ROD = UpgradeCostEntry.ofItem(Identifier.withDefaultNamespace("stick"), FULL_REPAIR_AMOUNT);

    // Tiers 2-6, shared by tools and armor alike.
    private static final List<UpgradeCostEntry> SHARED_ENTRY_BY_TIER_FROM_2 = List.of(
            UpgradeCostEntry.ofItem(Identifier.withDefaultNamespace("copper_ingot"), FULL_REPAIR_AMOUNT),
            UpgradeCostEntry.ofItem(Identifier.withDefaultNamespace("iron_ingot"), FULL_REPAIR_AMOUNT),
            UpgradeCostEntry.ofItem(Identifier.withDefaultNamespace("gold_ingot"), FULL_REPAIR_AMOUNT),
            UpgradeCostEntry.ofItem(Identifier.withDefaultNamespace("diamond"), FULL_REPAIR_AMOUNT),
            UpgradeCostEntry.ofItem(Identifier.withDefaultNamespace("netherite_ingot"), FULL_REPAIR_AMOUNT)
    );

    private RepairMaterials() {
    }

    /**
     * {@code tier}'s full-repair cost entry (amount always {@link #FULL_REPAIR_AMOUNT}) -- scale the
     * amount down yourself for a partial repair. {@code item} is only consulted for {@code tier < 0}
     * (the generic/off-ladder case), to special-case Fishing Rod away from the shared {@link
     * #GENERIC} material -- every on-ladder tier already has its own real entry, so it's ignored
     * otherwise.
     */
    static UpgradeCostEntry fullEntryFor(int tier, boolean isArmor, Item item) {
        if (tier < 0) {
            return item == Items.FISHING_ROD ? FISHING_ROD : GENERIC;
        }
        if (tier == 0) {
            return isArmor ? ARMOR_TIER_0 : TOOL_TIER_0;
        }
        if (tier == 1) {
            return isArmor ? ARMOR_TIER_1 : TOOL_TIER_1;
        }
        return SHARED_ENTRY_BY_TIER_FROM_2.get(tier - 2);
    }

    /** Same material as {@link #fullEntryFor}, but with {@code amount} units instead of the full-repair amount. */
    static UpgradeCostEntry scaledEntryFor(int tier, boolean isArmor, Item item, int amount) {
        UpgradeCostEntry base = fullEntryFor(tier, isArmor, item);
        return base.tag().isPresent()
                ? UpgradeCostEntry.ofTag(base.tag().get(), amount)
                : UpgradeCostEntry.ofItem(base.itemId().get(), amount);
    }
}
