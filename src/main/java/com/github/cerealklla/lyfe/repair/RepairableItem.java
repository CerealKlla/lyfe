package com.github.cerealklla.lyfe.repair;

import com.github.cerealklla.lyfe.craft.EquipmentTierLadder;

import net.minecraft.world.item.ItemStack;

/**
 * Which {@code craft.EquipmentTierLadder} tier (0-6) a held {@link ItemStack} represents, and
 * whether it's one of the 4 armor slots or a tool/weapon -- the two ladders share material names
 * from Tier 2 up, but diverge at Tiers 0-1 (Wood/Stone for tools vs. Leather/Chainmail for armor,
 * see {@link RepairMaterials}), so {@link RepairCost} needs both, not just the bare tier number.
 *
 * <p>{@link TierInfo#GENERIC} covers every other damageable vanilla item -- a shield, bow, trident,
 * elytra, fishing rod, etc. -- none of which this ladder has any tier concept for at all. Real bug
 * found live, 2026-10-07 ("I cannot repair shields"): this new Repair GUI fully replaces vanilla's
 * own Anvil/Grindstone menu, so an item outside the ladder entirely would otherwise become
 * permanently unrepairable with no other path at all. {@code null} is reserved for a genuinely
 * non-damageable item (nothing to repair, full stop).
 */
final class RepairableItem {

    record TierInfo(int tier, boolean armor) {
        static final TierInfo GENERIC = new TierInfo(-1, false);
    }

    private RepairableItem() {
    }

    static TierInfo of(ItemStack stack) {
        if (stack.isEmpty() || !stack.isDamageableItem()) {
            return null;
        }
        for (EquipmentTierLadder.ToolType tool : EquipmentTierLadder.ToolType.values()) {
            int tier = EquipmentTierLadder.tierOfItem(tool, stack.getItem());
            if (tier >= 0) {
                return new TierInfo(tier, false);
            }
        }
        for (var armorType : EquipmentTierLadder.ARMOR_TYPES) {
            int tier = EquipmentTierLadder.tierOfArmorItem(armorType, stack.getItem());
            if (tier >= 0) {
                return new TierInfo(tier, true);
            }
        }
        return TierInfo.GENERIC;
    }
}
