package com.github.cerealklla.lyfe.huntsman;

import java.util.HashMap;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;

/**
 * A single player's Quarry Knowledge progress -- raw cumulative kill count per mob entity type
 * (explicit user spec, 2026-10-16). Mirrors {@code research.PlayerResearch}'s attachment shape
 * (schema-versioned, one {@code Map<Identifier, Integer>} field), except there's no "learned"
 * terminal state here -- a mob type's count just keeps growing, with level/progress derived from
 * it on demand via {@link QuarryKnowledgeCurve}.
 */
public final class PlayerHuntsman {

    public static final int SCHEMA_VERSION = 1;

    public static final MapCodec<PlayerHuntsman> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.fieldOf("schema_version").forGetter(p -> p.schemaVersion),
            Codec.unboundedMap(Identifier.CODEC, Codec.INT).fieldOf("kills").forGetter(p -> p.kills)
    ).apply(i, PlayerHuntsman::new));

    private final int schemaVersion;
    private final Map<Identifier, Integer> kills;

    public PlayerHuntsman() {
        this(SCHEMA_VERSION, new HashMap<>());
    }

    private PlayerHuntsman(int schemaVersion, Map<Identifier, Integer> kills) {
        this.schemaVersion = schemaVersion;
        this.kills = new HashMap<>(kills);
    }

    public int kills(Identifier mobType) {
        return kills.getOrDefault(mobType, 0);
    }

    /** Records one more kill of {@code mobType} and returns the new cumulative total. */
    public int recordKill(Identifier mobType) {
        int updated = kills(mobType) + 1;
        kills.put(mobType, updated);
        return updated;
    }
}
