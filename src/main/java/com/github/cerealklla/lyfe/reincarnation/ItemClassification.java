package com.github.cerealklla.lyfe.reincarnation;

import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Heuristic "weapon, tool, torch, or food" classification for Reincarnation's type-filtered slots
 * (hotbar/offhand) — no single vanilla tag covers all four, so this is a judgment call, same shape
 * as Kyt's own "weapon" classification for its item palette (combat data/{@code ItemTags.SWORDS}),
 * flagged explicitly as a heuristic rather than an authoritative vanilla concept.
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
        return stack.is(ItemTags.SWORDS);
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
