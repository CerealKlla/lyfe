package com.github.cerealklla.lyfe.cook;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import com.github.cerealklla.nuclyus.server.PersistedServerStore;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

/**
 * The server-scoped (not world-scoped) generated food-recipe set -- a thin static facade over a
 * {@link PersistedServerStore}, direct mirror of {@code craft.ServerRecipeStore} (see that class's
 * own doc for the shared mechanics, pulled out to Nuclyus 2026-10-17).
 */
public final class ServerFoodRecipeStore {

    private static final PersistedServerStore<Identifier, GeneratedFoodRecipe> STORE =
            new PersistedServerStore<>("lyfe_food_recipes.json", GeneratedFoodRecipe.CODEC, GeneratedFoodRecipe::resultId) {
                @Override
                protected Map<Identifier, GeneratedFoodRecipe> generate() {
                    return ServerFoodRecipeStore.generate();
                }
            };

    private ServerFoodRecipeStore() {
    }

    public static Optional<GeneratedFoodRecipe> get(Identifier itemId) {
        return STORE.get(itemId);
    }

    public static Map<Identifier, GeneratedFoodRecipe> all() {
        return STORE.all();
    }

    public static void loadOrGenerate(MinecraftServer server) {
        STORE.loadOrGenerate(server);
    }

    /** Forces a brand-new random generation, overwriting the cached file -- backs {@code /lyfe rerollfoodrecipes}. */
    public static void reroll(MinecraftServer server) {
        STORE.reroll(server);
    }

    static Map<Identifier, GeneratedFoodRecipe> generate() {
        Random random = new Random();
        Map<Identifier, GeneratedFoodRecipe> result = new HashMap<>();
        for (Identifier resultId : FoodTierLadder.allResultIds()) {
            int tier = FoodTierLadder.tierOf(resultId);
            // Track A (real vanilla staples) are a fixed recipe, never randomized -- see
            // VanillaFoodRecipes' own doc (2026-10-06). Only Track B still rolls randomly.
            GeneratedFoodRecipe recipe = FoodTierLadder.isTrackB(resultId)
                    ? FoodRecipeGenerator.generate(resultId, tier, random)
                    : new GeneratedFoodRecipe(resultId, tier, VanillaFoodRecipes.get(resultId));
            result.put(resultId, recipe);
        }
        return result;
    }
}
