package com.github.cerealklla.lyfe.craft;

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
 * The server-scoped (not world-scoped) generated recipe set (design doc Section 19.4). Stored at
 * {@code MinecraftServer#getFile(FILE_NAME)} -- confirmed against the decompiled source as the
 * process's own working directory, a sibling of {@code server.properties}, NOT any single world's
 * save path ({@code getWorldPath(LevelResource)} is the wrong, per-world accessor). This is what
 * makes a world/map wipe leave recipes untouched while a real "server wipe" (deleting everything
 * alongside the world folders) naturally takes this file with it too.
 *
 * <p>Generated once, on first boot if the file is absent; loaded as-is on every subsequent boot.
 */
public final class ServerRecipeStore {

    private static final String FILE_NAME = "lyfe_equipment_recipes.json";
    private static final Codec<java.util.List<GeneratedRecipe>> LIST_CODEC = GeneratedRecipe.CODEC.listOf();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static volatile Map<Identifier, GeneratedRecipe> recipes = Map.of();

    private ServerRecipeStore() {
    }

    public static Optional<GeneratedRecipe> get(Identifier itemId) {
        return Optional.ofNullable(recipes.get(itemId));
    }

    public static Map<Identifier, GeneratedRecipe> all() {
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

    /**
     * Forces a brand-new random generation, overwriting the cached file -- added 2026-10-03 for the
     * {@code /lyfe rerollrecipes} debug command, so tuning {@code MaterialPool}/{@code RecipeGenerator}
     * can be iterated live without manually deleting the server's working-directory JSON and
     * rebooting between every change.
     */
    public static void reroll(MinecraftServer server) {
        Path file = server.getFile(FILE_NAME);
        recipes = generateAndSave(file);
    }

    private static Map<Identifier, GeneratedRecipe> generateAndSave(Path file) {
        Map<Identifier, GeneratedRecipe> generated = generate();
        save(file, generated);
        return generated;
    }

    static Map<Identifier, GeneratedRecipe> generate() {
        Random random = new Random();
        Map<Identifier, GeneratedRecipe> result = new HashMap<>();
        for (EquipmentTierLadder.ToolType tool : EquipmentTierLadder.ToolType.values()) {
            for (int tier = EquipmentTierLadder.MIN_TIER; tier <= EquipmentTierLadder.MAX_TIER; tier++) {
                Identifier itemId = EquipmentTierLadder.itemId(tool, tier);
                Identifier previousTierItem = tier > 0 ? EquipmentTierLadder.itemId(tool, tier - 1) : null;
                result.put(itemId, RecipeGenerator.generate(itemId, tier, previousTierItem, random));
            }
        }
        // Armor (added 2026-10-04, see decisions.md) -- same generation pipeline, no ToolType at all.
        for (var armorType : EquipmentTierLadder.ARMOR_TYPES) {
            for (int tier = EquipmentTierLadder.MIN_TIER; tier <= EquipmentTierLadder.MAX_TIER; tier++) {
                Identifier itemId = EquipmentTierLadder.armorItemId(armorType, tier);
                Identifier previousTierItem = tier > 0 ? EquipmentTierLadder.armorItemId(armorType, tier - 1) : null;
                result.put(itemId, RecipeGenerator.generate(itemId, tier, previousTierItem, random));
            }
        }
        // Ranged weapons (Bow/Crossbow, added 2026-10-08) -- each a single fixed-tier recipe, not a
        // per-tier family (see EquipmentTierLadder's own doc). No previousTierItem of their own.
        for (Identifier itemId : EquipmentTierLadder.allGeneratedRangedWeaponIds()) {
            int tier = EquipmentTierLadder.rangedWeaponTier(itemId);
            result.put(itemId, RecipeGenerator.generate(itemId, tier, null, random));
        }
        return result;
    }

    private static Optional<Map<Identifier, GeneratedRecipe>> load(Path file) {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement json = GSON.fromJson(reader, JsonElement.class);
            DataResult<java.util.List<GeneratedRecipe>> decoded = LIST_CODEC.parse(JsonOps.INSTANCE, json);
            return decoded.result().map(list -> list.stream()
                    .collect(Collectors.toMap(GeneratedRecipe::resultId, r -> r)));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static void save(Path file, Map<Identifier, GeneratedRecipe> toSave) {
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
