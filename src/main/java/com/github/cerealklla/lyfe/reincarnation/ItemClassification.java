package com.github.cerealklla.lyfe.reincarnation;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Heuristic "weapon, tool, torch, or food" classification for Reincarnation's type-filtered slots
 * (hotbar/offhand) — no single vanilla tag covers all four, so this is a judgment call, same shape
 * as Kyt's own "weapon" classification for its item palette (combat data/{@code ItemTags.SWORDS}),
 * flagged explicitly as a heuristic rather than an authoritative vanilla concept.
 *
 * <p><b>Real report, 2026-10-20</b>: "I had a custom crafted sword in slot 1" dropped on death --
 * confirmed by reading this class that {@code isWeapon} only ever checked {@code ItemTags.SWORDS},
 * which misses every weapon this suite's own crafting overhaul added outside the vanilla sword
 * family: {@code craft.EquipmentTierLadder.ToolType#SPEAR} (a wholly custom weapon type, no vanilla
 * tag covers it at all), Bow/Crossbow (real vanilla items, but never tagged as "swords" either), and
 * {@code craft.NatureStaffLadder}'s Nature Staff (a brand-new weapon family, same repeated
 * "new equipment family needs its own explicit wiring" gap already hit for Shop catalogs/other
 * systems this same session). Fixed per explicit follow-up request ("fix it so ANY weapon is saved,
 * not just vanilla") by checking every one of this suite's own known weapon sources directly,
 * instead of relying on a single vanilla tag that was only ever guaranteed to cover the one family
 * vanilla itself ships.
 */
public final class ItemClassification {

    private ItemClassification() {
    }

    public static boolean isProtectableType(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return isWeapon(stack) || isTool(stack) || isTorch(stack) || isFood(stack);
    }

    private static boolean isWeapon(ItemStack stack) {
        if (stack.is(ItemTags.SWORDS) || stack.is(Items.TRIDENT)) {
            return true;
        }
        Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (com.github.cerealklla.lyfe.craft.EquipmentTierLadder.tierOfItem(
                com.github.cerealklla.lyfe.craft.EquipmentTierLadder.ToolType.SPEAR, stack.getItem()) >= 0) {
            return true;
        }
        if (com.github.cerealklla.lyfe.craft.EquipmentTierLadder.rangedWeaponTier(itemId) >= 0) {
            return true;
        }
        return com.github.cerealklla.lyfe.craft.NatureStaffLadder.tierOfItem(itemId) >= 0;
    }

    private static boolean isTool(ItemStack stack) {
        return stack.is(ItemTags.AXES) || stack.is(ItemTags.PICKAXES)
                || stack.is(ItemTags.SHOVELS) || stack.is(ItemTags.HOES);
    }

    private static boolean isTorch(ItemStack stack) {
        return stack.is(Items.TORCH) || stack.is(Items.SOUL_TORCH);
    }

    private static boolean isFood(ItemStack stack) {
        return stack.get(DataComponents.FOOD) != null;
    }
}
