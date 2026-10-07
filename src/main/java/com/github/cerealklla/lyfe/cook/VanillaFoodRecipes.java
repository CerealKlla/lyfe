package com.github.cerealklla.lyfe.cook;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.resources.Identifier;

/**
 * Track A's real vanilla recipe shape, re-introduced as a fixed (never randomized) definition
 * (2026-10-06, explicit user request: "re-introduce the vanilla recipes as permanent, at tiers that
 * make sense"). Each result's real vanilla ingredient list, as an unordered item-id-to-quantity map
 * (same shape {@link GeneratedFoodRecipe} already uses, so nothing downstream needs to know these
 * aren't randomly generated) -- confirmed against vanilla's own real recipes before {@code
 * VanillaFoodRecipeStripper} removes them from the plain crafting table/furnace (which still happens
 * unchanged -- these items still only come from the matching-tier Cooking Structure, per explicit
 * user confirmation; "permanent" means the ingredients are fixed, not that the Cooking Structure
 * requirement goes away). {@link CookingStructureMenu#computeCraftable} treats every result here as
 * always-known, no Research Note required -- the same "basic staple, everyone already knows this"
 * treatment {@code craft.EquipmentTierLadder}'s Tier 0 (Wood)/Leather already gets, just without a
 * literal Tier 0 to hang off of (food's own ladder starts at 1). The real food value/display name a
 * player actually sees still gets fully overwritten by {@code CookingListener#bakeIcons} same as
 * every other craft, per explicit user confirmation ("despite them being vanilla recipes I do want
 * their food values overwritten to use my custom formula and naming convention") -- these are only
 * ever the real vanilla *ingredients*, never the real vanilla nutrition/name.
 */
final class VanillaFoodRecipes {

    private static final Map<Identifier, Map<Identifier, Integer>> RECIPES = buildRecipes();

    private VanillaFoodRecipes() {
    }

    static Map<Identifier, Integer> get(Identifier resultId) {
        Map<Identifier, Integer> recipe = RECIPES.get(resultId);
        if (recipe == null) {
            throw new IllegalArgumentException("No vanilla recipe recorded for Track A result: " + resultId);
        }
        return recipe;
    }

    private static Map<Identifier, Map<Identifier, Integer>> buildRecipes() {
        Map<Identifier, Map<Identifier, Integer>> recipes = new LinkedHashMap<>();
        // Tier 1 -- plain smelting pairs (1 raw -> 1 cooked) plus the basic staples.
        recipes.put(id("cooked_beef"), of(id("beef"), 1));
        recipes.put(id("cooked_porkchop"), of(id("porkchop"), 1));
        recipes.put(id("cooked_chicken"), of(id("chicken"), 1));
        recipes.put(id("cooked_mutton"), of(id("mutton"), 1));
        recipes.put(id("cooked_rabbit"), of(id("rabbit"), 1));
        recipes.put(id("cooked_cod"), of(id("cod"), 1));
        recipes.put(id("cooked_salmon"), of(id("salmon"), 1));
        recipes.put(id("baked_potato"), of(id("potato"), 1));
        recipes.put(id("bread"), of(id("wheat"), 3));
        recipes.put(id("cookie"), of(id("wheat"), 2, id("cocoa_beans"), 1));
        recipes.put(id("mushroom_stew"), of(id("bowl"), 1, id("red_mushroom"), 1, id("brown_mushroom"), 1));
        // Tier 2.
        recipes.put(id("beetroot_soup"), of(id("beetroot"), 6, id("bowl"), 1));
        recipes.put(id("pumpkin_pie"), of(id("pumpkin"), 1, id("sugar"), 1, id("egg"), 1));
        recipes.put(id("golden_carrot"), of(id("carrot"), 1, id("gold_nugget"), 8));
        // Tier 3.
        recipes.put(id("cake"), of(id("milk_bucket"), 3, id("sugar"), 2, id("egg"), 1, id("wheat"), 3));
        recipes.put(id("rabbit_stew"), of(id("cooked_rabbit"), 1, id("baked_potato"), 1, id("carrot"), 1, id("brown_mushroom"), 1, id("bowl"), 1));
        // Tier 4.
        recipes.put(id("golden_apple"), of(id("apple"), 1, id("gold_nugget"), 8));
        // Tier 5.
        recipes.put(id("enchanted_golden_apple"), of(id("golden_apple"), 1, id("gold_block"), 8));
        return Map.copyOf(recipes);
    }

    private static Identifier id(String path) {
        return Identifier.withDefaultNamespace(path);
    }

    private static Map<Identifier, Integer> of(Identifier a, int qa) {
        Map<Identifier, Integer> map = new LinkedHashMap<>();
        map.put(a, qa);
        return map;
    }

    private static Map<Identifier, Integer> of(Identifier a, int qa, Identifier b, int qb) {
        Map<Identifier, Integer> map = of(a, qa);
        map.put(b, qb);
        return map;
    }

    private static Map<Identifier, Integer> of(Identifier a, int qa, Identifier b, int qb, Identifier c, int qc) {
        Map<Identifier, Integer> map = of(a, qa, b, qb);
        map.put(c, qc);
        return map;
    }

    private static Map<Identifier, Integer> of(Identifier a, int qa, Identifier b, int qb, Identifier c, int qc, Identifier d, int qd) {
        Map<Identifier, Integer> map = of(a, qa, b, qb, c, qc);
        map.put(d, qd);
        return map;
    }

    private static Map<Identifier, Integer> of(Identifier a, int qa, Identifier b, int qb, Identifier c, int qc, Identifier d, int qd, Identifier e, int qe) {
        Map<Identifier, Integer> map = of(a, qa, b, qb, c, qc, d, qd);
        map.put(e, qe);
        return map;
    }
}
