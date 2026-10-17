package com.github.cerealklla.lyfe.socket;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
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
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.server.MinecraftServer;

/**
 * The server-scoped (not world-scoped) randomized Runeword-to-rune-sequence mapping (explicit user
 * spec: "These combinations do get randomized with the world wipe, just like recipes"). Direct
 * structural mirror of {@code craft.ServerRecipeStore}/{@code cook.ServerFoodRecipeStore} -- same
 * "generate once on first boot, persist to the server's own working directory, reuse thereafter"
 * shape, same reason (a world-save wipe shouldn't re-shuffle which runes make which Runeword, but a
 * real server wipe naturally does).
 *
 * <p>{@link RunewordCatalog}'s 60 {@link RunewordDefinition}s (name/category/effect/rune COUNT) are
 * fixed and never change; only each one's actual {@link RuneId} SEQUENCE is rolled here, once, at
 * {@code runeCount} distinct random runes in random order.
 */
public final class ServerRunewordStore {

    private static final String FILE_NAME = "lyfe_runewords.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public record GeneratedRuneword(String name, List<RuneId> sequence) {
        static final Codec<GeneratedRuneword> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("name").forGetter(GeneratedRuneword::name),
                RuneId.CODEC.listOf().fieldOf("sequence").forGetter(GeneratedRuneword::sequence)
        ).apply(i, GeneratedRuneword::new));
    }

    private static final Codec<List<GeneratedRuneword>> LIST_CODEC = GeneratedRuneword.CODEC.listOf();

    // Keyed by Runeword name for persistence lookup; also indexed by (category, sequence) for live matching.
    private static volatile Map<String, List<RuneId>> sequenceByName = Map.of();

    private ServerRunewordStore() {
    }

    public static void loadOrGenerate(MinecraftServer server) {
        Path file = server.getFile(FILE_NAME);
        if (Files.exists(file)) {
            sequenceByName = load(file).orElseGet(() -> generateAndSave(file));
        } else {
            sequenceByName = generateAndSave(file);
        }
        // RuneWorth's own per-rune rarity is entirely derived from this mapping -- must be
        // recomputed every time it changes, not just once at class-load (2026-10-18).
        RuneWorth.recompute();
    }

    /** Forces a brand-new random generation, overwriting the cached file -- mirrors {@code ServerRecipeStore#reroll}, for a matching future {@code /lyfe rerollrunewords} debug command. */
    public static void reroll(MinecraftServer server) {
        Path file = server.getFile(FILE_NAME);
        sequenceByName = generateAndSave(file);
        RuneWorth.recompute();
    }

    private static Map<String, List<RuneId>> generateAndSave(Path file) {
        Map<String, List<RuneId>> generated = generate();
        save(file, generated);
        return generated;
    }

    private static Map<String, List<RuneId>> generate() {
        Random random = new Random();
        Map<String, List<RuneId>> result = new HashMap<>();
        for (RunewordDefinition def : RunewordCatalog.all()) {
            List<RuneId> pool = new ArrayList<>(List.of(RuneId.values()));
            java.util.Collections.shuffle(pool, random);
            result.put(def.name(), List.copyOf(pool.subList(0, def.runeCount())));
        }
        return result;
    }

    /**
     * The active {@link RunewordDefinition} for {@code sockets} on an item of {@code category}, or
     * {@code null} if none matches. A match requires every socket to be a {@link SocketSlot.Kind#RUNE}
     * (no gems, no empty sockets mixed in) in the exact rolled order, AND the item's own category to
     * match the Runeword's -- "this Runeword cannot be used in a Tool or Armor; if you tried you would
     * simply get the individual benefits" (explicit user spec, enforced by this category check alone).
     */
    public static RunewordDefinition match(EquipmentCategory category, List<SocketSlot> sockets) {
        if (sockets.isEmpty()) {
            return null;
        }
        List<RuneId> ordered = new ArrayList<>(sockets.size());
        for (SocketSlot slot : sockets) {
            if (slot.kind() != SocketSlot.Kind.RUNE) {
                return null;
            }
            ordered.add(slot.runeId().orElseThrow());
        }
        for (RunewordDefinition def : definitionsFor(category)) {
            List<RuneId> sequence = sequenceByName.get(def.name());
            if (sequence != null && sequence.equals(ordered)) {
                return def;
            }
        }
        return null;
    }

    private static List<RunewordDefinition> definitionsFor(EquipmentCategory category) {
        return switch (category) {
            case WEAPON -> RunewordCatalog.WEAPON;
            case ARMOR -> RunewordCatalog.ARMOR;
            case TOOL -> RunewordCatalog.TOOL;
        };
    }

    /** The real rune sequence for {@code definitionName}, for a future "identify this Runeword" tooltip/UI feature -- not consumed yet. */
    public static List<RuneId> sequenceFor(String definitionName) {
        return sequenceByName.getOrDefault(definitionName, List.of());
    }

    private static Optional<Map<String, List<RuneId>>> load(Path file) {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement json = GSON.fromJson(reader, JsonElement.class);
            DataResult<List<GeneratedRuneword>> decoded = LIST_CODEC.parse(JsonOps.INSTANCE, json);
            return decoded.result().map(list -> list.stream()
                    .collect(Collectors.toMap(GeneratedRuneword::name, GeneratedRuneword::sequence, (a, b) -> a, LinkedHashMap::new)));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static void save(Path file, Map<String, List<RuneId>> toSave) {
        List<GeneratedRuneword> list = toSave.entrySet().stream()
                .map(e -> new GeneratedRuneword(e.getKey(), e.getValue()))
                .toList();
        DataResult<JsonElement> encoded = LIST_CODEC.encodeStart(JsonOps.INSTANCE, list);
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
