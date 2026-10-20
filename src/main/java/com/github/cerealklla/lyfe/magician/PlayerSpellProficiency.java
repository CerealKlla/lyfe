package com.github.cerealklla.lyfe.magician;

import java.util.HashMap;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;

/**
 * A single player's raw cumulative Spell Proficiency progress, per spell (design doc Section 24:
 * "tracks proficiency by spell"). Mirrors {@code huntsman.PlayerHuntsman}'s exact shape -- a plain
 * {@code Map<Identifier, Integer>} of raw progress, with level/cost/cooldown all derived on demand
 * via {@link SpellProficiencyCurve} rather than stored directly.
 */
public final class PlayerSpellProficiency {

    public static final int SCHEMA_VERSION = 1;

    public static final MapCodec<PlayerSpellProficiency> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.fieldOf("schema_version").forGetter(p -> p.schemaVersion),
            Codec.unboundedMap(Identifier.CODEC, Codec.INT).fieldOf("progress").forGetter(p -> p.progress)
    ).apply(i, PlayerSpellProficiency::new));

    private final int schemaVersion;
    private final Map<Identifier, Integer> progress;

    public PlayerSpellProficiency() {
        this(SCHEMA_VERSION, new HashMap<>());
    }

    private PlayerSpellProficiency(int schemaVersion, Map<Identifier, Integer> progress) {
        this.schemaVersion = schemaVersion;
        this.progress = new HashMap<>(progress);
    }

    public int progress(Identifier spellId) {
        return progress.getOrDefault(spellId, 0);
    }

    /** Adds {@code amount} progress toward {@code spellId} and returns the new cumulative total. */
    public int addProgress(Identifier spellId, int amount) {
        int updated = progress(spellId) + amount;
        progress.put(spellId, updated);
        return updated;
    }
}
