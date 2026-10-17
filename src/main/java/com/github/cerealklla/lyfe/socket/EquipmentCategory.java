package com.github.cerealklla.lyfe.socket;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

/**
 * Which of the three socket-stat categories (explicit user spec: "Offensive stats when placed in
 * Weapons, Defensive stats when placed in armor, and utility stats when placed in tools") a given
 * item falls under -- classified purely from real vanilla item-data components, not a hand-authored
 * item-id list, so any current or future weapon/armor/tool (vanilla or modded) is automatically
 * covered. Mirrors {@code settlemynts.guardhouse.GuardLootDeposit}'s own armor/weapon classification
 * precedent ({@code DataComponents.EQUIPPABLE}/{@code DataComponents.WEAPON}), extended with {@code
 * DataComponents.TOOL} for the third category. Priority WEAPON &gt; ARMOR &gt; TOOL on the rare item
 * that somehow carries more than one component (none do in practice).
 */
public enum EquipmentCategory {
    WEAPON, ARMOR, TOOL;

    /** {@code null} if {@code stack} is none of the three (not socketable at all). */
    public static EquipmentCategory of(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        if (stack.has(DataComponents.WEAPON)) {
            return WEAPON;
        }
        if (stack.has(DataComponents.EQUIPPABLE)) {
            return ARMOR;
        }
        if (stack.has(DataComponents.TOOL)) {
            return TOOL;
        }
        return null;
    }
}
