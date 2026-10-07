package com.github.cerealklla.lyfe.durability;

import net.minecraft.world.item.ItemStack;

/**
 * Shared "has this item effectively reached 0 durability" predicate, used by every piece of the
 * Unbreakable Equipment feature (the mixin clamps damage at exactly {@code maxDamage - 1}, so this
 * is the stable terminal state to check for, not a race-prone {@code == maxDamage} comparison).
 */
public final class UnbreakableConstants {

    private UnbreakableConstants() {
    }

    public static boolean isAtZero(ItemStack stack) {
        return stack.isDamageableItem() && stack.getDamageValue() >= stack.getMaxDamage() - 1;
    }
}
