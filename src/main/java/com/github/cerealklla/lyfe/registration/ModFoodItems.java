package com.github.cerealklla.lyfe.registration;

import com.github.cerealklla.lyfe.LyfeMod;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Track B's 5 "signature dish" items (design doc Section 19.3, the entree chain, 2026-10-03) --
 * placeholder names/icons this pass, same "no new art needed" precedent as {@code
 * rest.StoolBlock} reusing oak planks (see per-item texture notes in {@code
 * cook.FoodTierLadder}'s class doc). Base {@link FoodProperties} here are never actually seen by a
 * player -- every real instance gets its nutrition/name overwritten at craft time by {@code
 * cook.CookingListener#bakeIcons} -- so these are just safe, harmless placeholders, not tuned.
 */
public final class ModFoodItems {

    private ModFoodItems() {
    }

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(LyfeMod.MODID);

    public static final DeferredItem<Item> HEARTY_BISCUIT = register("hearty_biscuit");
    public static final DeferredItem<Item> MEAT_LOAF = register("meat_loaf");
    public static final DeferredItem<Item> SHEPHERDS_PLATTER = register("shepherds_platter");
    public static final DeferredItem<Item> BANQUET_PLATE = register("banquet_plate");
    public static final DeferredItem<Item> FEAST_OF_THE_KYNGDOMS = register("feast_of_the_kyngdoms");

    // Not a Track B signature dish -- a Track A (fixed, non-randomized) Tier 1 recipe added
    // 2026-10-10 (explicit request: "a T1 Cooked Fish recipe which just takes 1 Fish Meat"), same
    // VanillaFoodRecipes-backed shape as cooked_beef/cooked_porkchop etc., just a custom item since
    // vanilla has no "Cooked Fish" of its own (COOKED_FISH_MEAT, the old pre-unified-cooking version
    // of this exact item, was removed 2026-10-03 -- see ModItems.FISH_MEAT's own doc). Registered
    // here rather than ModItems since it's a crafted dish, not a raw ingredient.
    public static final DeferredItem<Item> COOKED_FISH = register("cooked_fish");

    private static DeferredItem<Item> register(String name) {
        return ITEMS.register(name, id -> new Item(new Item.Properties()
                .food(new FoodProperties.Builder().nutrition(1).saturationModifier(0.1F).build())
                .setId(ResourceKey.create(Registries.ITEM, id))));
    }
}
