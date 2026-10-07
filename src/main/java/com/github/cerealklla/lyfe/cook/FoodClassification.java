package com.github.cerealklla.lyfe.cook;

import java.util.Set;

import com.github.cerealklla.lyfe.registration.ModItems;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Food Components vs. Crafted food (design doc Section 19.3, Appendix C) -- a raw/gathered food
 * item restores exactly 1 hunger icon (= 2 nutrition points, vanilla's own icon/point ratio) no
 * matter how it was obtained, and this never changes at any skill level or structure tier. Replaces
 * {@code hunger.CookedFoods}, which backed the now-retired Section 10.2 flat-bonus Cook mechanic.
 *
 * <p>{@link #COMPONENTS} is deliberately narrower than {@code cook.FoodMaterialPool}'s own
 * ingredient pool -- plain non-edible ingredients (Wheat, Egg, Sugar, Milk Bucket) feed recipe
 * generation but were never food in the first place, so they have no "restores 1 icon" rule to
 * apply at all.
 */
public final class FoodClassification {

    private FoodClassification() {
    }

    public static final Set<Item> COMPONENTS = Set.of(
            Items.APPLE, Items.CARROT, Items.POTATO, Items.BEETROOT, Items.MELON_SLICE,
            Items.SWEET_BERRIES, Items.GLOW_BERRIES, Items.CHORUS_FRUIT, Items.HONEY_BOTTLE,
            Items.BEEF, Items.PORKCHOP, Items.CHICKEN, Items.MUTTON, Items.RABBIT,
            Items.COD, Items.SALMON, ModItems.FISH_MEAT.get()
    );

    public static boolean isComponent(Item item) {
        return COMPONENTS.contains(item);
    }
}
