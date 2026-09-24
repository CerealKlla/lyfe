package com.github.cerealklla.lyfe.hunger;

import java.util.Set;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Which food items count as "cooked" for the Cook skill (design doc Section 10.2). Vanilla has no
 * tag for this concept, so this is a hand-maintained set rather than a data-driven one -- should
 * probably become a real {@code TagKey<Item>} (e.g. {@code lyfe:cooked_foods}) later so other mods
 * (and datapacks) can extend it without a code change; not worth that for a first version.
 */
final class CookedFoods {

    static final Set<Item> ITEMS = Set.of(
            Items.COOKED_BEEF,
            Items.COOKED_PORKCHOP,
            Items.COOKED_CHICKEN,
            Items.COOKED_MUTTON,
            Items.COOKED_RABBIT,
            Items.COOKED_SALMON,
            Items.COOKED_COD,
            Items.BAKED_POTATO,
            Items.BREAD,
            Items.PUMPKIN_PIE,
            Items.RABBIT_STEW,
            Items.MUSHROOM_STEW,
            Items.BEETROOT_SOUP
    );

    private CookedFoods() {
    }

    static boolean isCooked(Item item) {
        return ITEMS.contains(item);
    }
}
