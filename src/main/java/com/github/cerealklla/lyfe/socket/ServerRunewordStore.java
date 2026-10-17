package com.github.cerealklla.lyfe.socket;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.github.cerealklla.nuclyus.server.PersistedServerStore;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.server.MinecraftServer;

/**
 * The server-scoped (not world-scoped) randomized Runeword-to-rune-sequence mapping (explicit user
 * spec: "These combinations do get randomized with the world wipe, just like recipes") -- a thin
 * static facade over a {@link PersistedServerStore}, direct structural mirror of {@code
 * craft.ServerRecipeStore}/{@code cook.ServerFoodRecipeStore} (see that class's own doc for the
 * shared mechanics, pulled out to Nuclyus 2026-10-17).
 *
 * <p>{@link RunewordCatalog}'s 60 {@link RunewordDefinition}s (name/category/effect/rune COUNT) are
 * fixed and never change; only each one's actual {@link RuneId} SEQUENCE is rolled here, once, at
 * {@code runeCount} distinct random runes in random order.
 */
public final class ServerRunewordStore {

    public record GeneratedRuneword(String name, List<RuneId> sequence) {
        static final Codec<GeneratedRuneword> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("name").forGetter(GeneratedRuneword::name),
                RuneId.CODEC.listOf().fieldOf("sequence").forGetter(GeneratedRuneword::sequence)
        ).apply(i, GeneratedRuneword::new));
    }

    private static final PersistedServerStore<String, GeneratedRuneword> STORE =
            new PersistedServerStore<>("lyfe_runewords.json", GeneratedRuneword.CODEC, GeneratedRuneword::name) {
                @Override
                protected Map<String, GeneratedRuneword> generate() {
                    return ServerRunewordStore.generate();
                }

                @Override
                protected void onLoaded() {
                    // RuneWorth's own per-rune rarity is entirely derived from this mapping -- must be
                    // recomputed every time it changes, not just once at class-load (2026-10-18).
                    RuneWorth.recompute();
                }
            };

    private ServerRunewordStore() {
    }

    public static void loadOrGenerate(MinecraftServer server) {
        STORE.loadOrGenerate(server);
    }

    /** Forces a brand-new random generation, overwriting the cached file -- mirrors {@code ServerRecipeStore#reroll}, for a matching future {@code /lyfe rerollrunewords} debug command. */
    public static void reroll(MinecraftServer server) {
        STORE.reroll(server);
    }

    private static Map<String, GeneratedRuneword> generate() {
        Random random = new Random();
        Map<String, GeneratedRuneword> result = new HashMap<>();
        for (RunewordDefinition def : RunewordCatalog.all()) {
            List<RuneId> pool = new ArrayList<>(List.of(RuneId.values()));
            Collections.shuffle(pool, random);
            result.put(def.name(), new GeneratedRuneword(def.name(), List.copyOf(pool.subList(0, def.runeCount()))));
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
            List<RuneId> sequence = sequenceFor(def.name());
            if (!sequence.isEmpty() && sequence.equals(ordered)) {
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
        return STORE.get(definitionName).map(GeneratedRuneword::sequence).orElse(List.of());
    }
}
