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

    /**
     * Player-facing label for "this Runeword only works on a ___" tooltips (explicit user report,
     * 2026-10-30: a Runeword's tooltip never said which item type it needed, so a player could
     * socket the right runes into the wrong kind of item and see nothing happen with no clue why).
     * Single source of truth for both {@code LyfeModClient#onSocketTooltip} (the item's own hover
     * tooltip) and {@code menu.client.RuneCodexTab} (the "Runewords Discovered" Codex screen) --
     * previously only added to the former, missed the latter, which is why a fix that looked
     * complete from one screen wasn't actually showing up on the other.
     *
     * <p>{@code WEAPON} reads plain "Weapon," not "Main Hand Weapon" (corrected same day) -- the
     * Rune/Gem system deliberately has no main-hand/off-hand distinction at all (see {@link
     * RunewordEffectListener#onIncomingDamage}, which now checks both hands for a chance-on-hit
     * proc), so a label implying one was actively misleading.
     */
    public String displayLabel() {
        return switch (this) {
            case WEAPON -> "Weapon";
            case ARMOR -> "Armor";
            case TOOL -> "Tool";
        };
    }

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
