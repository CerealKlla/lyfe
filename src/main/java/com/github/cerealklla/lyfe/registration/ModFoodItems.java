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

    private static DeferredItem<Item> register(String name) {
        return ITEMS.register(name, id -> new Item(new Item.Properties()
                .food(new FoodProperties.Builder().nutrition(1).saturationModifier(0.1F).build())
                .setId(ResourceKey.create(Registries.ITEM, id))));
    }
}
