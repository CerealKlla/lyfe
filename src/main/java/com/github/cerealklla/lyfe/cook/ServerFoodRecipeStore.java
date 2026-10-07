package com.github.cerealklla.lyfe.cook;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

/**
 * The server-scoped (not world-scoped) generated food-recipe set -- direct mirror of {@code
 * craft.ServerRecipeStore}, its own file so a world wipe doesn't touch it but a real server wipe
 * does (design doc Section 19.4's "seasonal server wipe" model, extended to food).
 */
public final class ServerFoodRecipeStore {

    private static final String FILE_NAME = "lyfe_food_recipes.json";
    private static final Codec<java.util.List<GeneratedFoodRecipe>> LIST_CODEC = GeneratedFoodRecipe.CODEC.listOf();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static volatile Map<Identifier, GeneratedFoodRecipe> recipes = Map.of();

    private ServerFoodRecipeStore() {
    }

    public static Optional<GeneratedFoodRecipe> get(Identifier itemId) {
        return Optional.ofNullable(recipes.get(itemId));
    }

    public static Map<Identifier, GeneratedFoodRecipe> all() {
        return Map.copyOf(recipes);
    }

    public static void loadOrGenerate(MinecraftServer server) {
        Path file = server.getFile(FILE_NAME);
        if (Files.exists(file)) {
            recipes = load(file).orElseGet(() -> generateAndSave(file));
        } else {
            recipes = generateAndSave(file);
        }
    }

    /** Forces a brand-new random generation, overwriting the cached file -- backs {@code /lyfe rerollfoodrecipes}. */
    public static void reroll(MinecraftServer server) {
        Path file = server.getFile(FILE_NAME);
        recipes = generateAndSave(file);
    }

    private static Map<Identifier, GeneratedFoodRecipe> generateAndSave(Path file) {
        Map<Identifier, GeneratedFoodRecipe> generated = generate();
        save(file, generated);
        return generated;
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

    private static Optional<Map<Identifier, GeneratedFoodRecipe>> load(Path file) {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement json = GSON.fromJson(reader, JsonElement.class);
            DataResult<java.util.List<GeneratedFoodRecipe>> decoded = LIST_CODEC.parse(JsonOps.INSTANCE, json);
            return decoded.result().map(list -> list.stream()
                    .collect(Collectors.toMap(GeneratedFoodRecipe::resultId, r -> r)));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static void save(Path file, Map<Identifier, GeneratedFoodRecipe> toSave) {
        DataResult<JsonElement> encoded = LIST_CODEC.encodeStart(JsonOps.INSTANCE, java.util.List.copyOf(toSave.values()));
        encoded.result().ifPresent(json -> {
            try {
                Files.createDirectories(file.toAbsolutePath().getParent());
                try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                    GSON.toJson(json, writer);
                }
            } catch (IOException ignored) {
                // Best-effort persistence -- a failed save just means regeneration next boot, not a crash.
            }
        });
    }
}
