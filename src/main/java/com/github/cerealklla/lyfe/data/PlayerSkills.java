package com.github.cerealklla.lyfe.data;

import java.util.HashMap;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import com.github.cerealklla.lyfe.skill.SkillId;

/**
 * A single player's skill XP, persisted via NeoForge's Data Attachment API (design doc Section 13,
 * verified against the decompiled 26.1.2.109 source before use). Mutated in place rather than
 * rebuilt on every XP gain — entity attachments piggyback on the holding entity's own save
 * routine, so no explicit dirty-marking is needed here (unlike Cartographyr's world-level
 * {@code SavedData}, which does require it).
 */
public final class PlayerSkills {

    public static final int SCHEMA_VERSION = 1;

    public static final MapCodec<PlayerSkills> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.fieldOf("schema_version").forGetter(p -> p.schemaVersion),
            Codec.unboundedMap(SkillId.CODEC, Codec.LONG).fieldOf("xp").forGetter(p -> p.xp)
    ).apply(i, PlayerSkills::new));

    private final int schemaVersion;
    private final Map<SkillId, Long> xp;

    public PlayerSkills() {
        this(SCHEMA_VERSION, new HashMap<>());
    }

    private PlayerSkills(int schemaVersion, Map<SkillId, Long> xp) {
        this.schemaVersion = schemaVersion;
        this.xp = new HashMap<>(xp);
    }

    public long getXp(SkillId skillId) {
        return xp.getOrDefault(skillId, 0L);
    }

    /** Adds XP for a skill (total never drops below 0) and returns the new total. */
    public long addXp(SkillId skillId, long amount) {
        long newTotal = Math.max(0, getXp(skillId) + amount);
        xp.put(skillId, newTotal);
        return newTotal;
    }

    /** Every skill this player has any XP recorded for. For UI/inspection use (e.g. the future skill tree, Section 12). */
    public Map<SkillId, Long> allXp() {
        return Map.copyOf(xp);
    }
}
