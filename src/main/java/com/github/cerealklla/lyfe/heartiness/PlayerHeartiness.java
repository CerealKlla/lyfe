package com.github.cerealklla.lyfe.heartiness;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.util.Mth;

/**
 * Tracks how much of a player's *current* missing health is attributable to mob-caused damage
 * (design doc, 2026-10-03 playtest correction) -- healing drains this portion after the rest of the
 * missing health (see {@link HeartinessListener#onLivingHeal}), and HP recovered from it grants
 * {@link HeartinessConstants#MOB_DAMAGE_XP_MULTIPLIER}x the base XP rate instead of 1x. Not synced
 * -- purely a server-side bookkeeping value, same as {@code research.PlayerResearch}.
 */
public final class PlayerHeartiness {

    public static final int SCHEMA_VERSION = 1;

    public static final MapCodec<PlayerHeartiness> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.fieldOf("schema_version").forGetter(p -> p.schemaVersion),
            Codec.FLOAT.fieldOf("mob_missing_health").forGetter(p -> p.mobMissingHealth)
    ).apply(i, PlayerHeartiness::new));

    private final int schemaVersion;
    private float mobMissingHealth;

    public PlayerHeartiness() {
        this(SCHEMA_VERSION, 0.0F);
    }

    private PlayerHeartiness(int schemaVersion, float mobMissingHealth) {
        this.schemaVersion = schemaVersion;
        this.mobMissingHealth = mobMissingHealth;
    }

    public float getMobMissingHealth() {
        return mobMissingHealth;
    }

    /**
     * Clamps against the real current total missing health -- self-corrects any desync (e.g. a
     * full-heal respawn carrying a stale value forward via copyOnDeath()). Called at the start of
     * every damage/heal handler, so it's always accurate by the time it's actually used.
     */
    public void clampTo(float totalMissingHealth) {
        mobMissingHealth = Mth.clamp(mobMissingHealth, 0.0F, totalMissingHealth);
    }

    public void bankMobDamage(float amount) {
        mobMissingHealth += amount;
    }

    /** Drains up to {@code amount}, never below 0; returns how much was actually drained. */
    public float drain(float amount) {
        float drained = Math.min(mobMissingHealth, amount);
        mobMissingHealth -= drained;
        return drained;
    }
}
