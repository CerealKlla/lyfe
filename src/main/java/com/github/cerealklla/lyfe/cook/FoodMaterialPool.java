package com.github.cerealklla.lyfe.cook;

import java.util.List;
import java.util.Map;

import com.github.cerealklla.lyfe.registration.ModItems;

import net.minecraft.resources.Identifier;

/**
 * A curated, hand-maintained ingredient pool per tier, used only to weight random food-recipe
 * generation ({@code FoodRecipeGenerator}) -- mirrors {@code craft.MaterialPool}'s role exactly,
 * same "tunable first pass, not an exhaustive catalog" convention. Deliberately broader than {@code
 * FoodClassification#COMPONENTS} -- plain non-edible ingredients (Wheat, Egg, Sugar, Milk Bucket)
 * belong here since real recipes use them, even though they have no "restores 1 icon" rule of their
 * own to apply.
 */
final class FoodMaterialPool {

    private static final Map<Integer, List<Identifier>> TIER_POOLS = Map.of(
            1, idList("apple", "carrot", "potato", "wheat", "egg", "sugar", "beef", "chicken", "porkchop",
                    "mutton", "rabbit", "cod", "salmon", "milk_bucket"),
            2, idList("beetroot", "pumpkin", "melon_slice", "sweet_berries"),
            3, idList("honey_bottle", "glow_berries", "cocoa_beans", "nether_wart"),
            4, idList("gold_nugget", "chorus_fruit"),
            5, idList("gold_ingot")
    );

    private FoodMaterialPool() {
    }

    /**
     * Fish Meat (design doc, 2026-10-03 Fisherman addition) slots in as a Tier 1 ingredient now that
     * it's a real raw item -- resolved lazily per call, not cached in a static field: a {@code
     * DeferredItem} isn't safely readable until after mod registration events run, and this class
     * can plausibly be touched (e.g. by a test) before that.
     */
    static List<Identifier> poolForTier(int tier) {
        int clamped = Math.max(FoodTierLadder.MIN_TIER, Math.min(FoodTierLadder.MAX_TIER, tier));
        if (clamped == 1) {
            List<Identifier> withFish = new java.util.ArrayList<>(TIER_POOLS.get(1));
            withFish.add(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(ModItems.FISH_MEAT.get()));
            return List.copyOf(withFish);
        }
        return TIER_POOLS.get(clamped);
    }

    private static List<Identifier> idList(String... names) {
        return List.of(names).stream().map(Identifier::withDefaultNamespace).toList();
    }
}
