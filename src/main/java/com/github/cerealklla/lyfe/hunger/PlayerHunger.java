package com.github.cerealklla.lyfe.hunger;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.util.Mth;

/**
 * A single player's true hunger state (design doc Section 10.0's "decoupled hunger" mechanism).
 * This is the real source of truth for gameplay -- vanilla's own {@code FoodData} keeps running
 * untouched underneath and is never itself the authority once this attachment exists; see
 * {@link HungerListener}.
 */
public final class PlayerHunger {

    public static final int SCHEMA_VERSION = 1;

    public static final MapCodec<PlayerHunger> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.fieldOf("schema_version").forGetter(p -> p.schemaVersion),
            Codec.INT.fieldOf("true_hunger").forGetter(p -> p.trueHunger),
            Codec.FLOAT.fieldOf("true_saturation").forGetter(p -> p.trueSaturation)
    ).apply(i, PlayerHunger::new));

    private final int schemaVersion;
    private int trueHunger;
    private float trueSaturation;

    public PlayerHunger() {
        this(SCHEMA_VERSION, HungerConstants.BASE_MAX_HUNGER, HungerConstants.START_SATURATION);
    }

    private PlayerHunger(int schemaVersion, int trueHunger, float trueSaturation) {
        this.schemaVersion = schemaVersion;
        this.trueHunger = trueHunger;
        this.trueSaturation = trueSaturation;
    }

    public int getTrueHunger() {
        return trueHunger;
    }

    public float getTrueSaturation() {
        return trueSaturation;
    }

    /**
     * Deducts a real, absolute point loss (never scaled) observed from vanilla's own exhaustion
     * math -- spends {@code trueSaturation} first and only touches {@code trueHunger} once it's
     * depleted, the same two-branch shape vanilla's own {@code FoodData#tick} uses on its real
     * fields ("if (saturationLevel > 0) spend saturation; else drop foodLevel").
     *
     * <p><b>Real bug fixed 2026-10-10</b>: this used to unconditionally decrement {@code trueHunger}
     * every time and only ever *clamp* {@code trueSaturation} down afterward, never actually
     * spending it -- so saturation (including the real starting 5.0 every fresh player has) did
     * nothing at all: true hunger started dropping immediately on the very next exhaustion crossing
     * regardless of how much saturation was sitting there, with no "well fed, hunger isn't dropping
     * yet" grace period after eating at all (the exact behavior a real report described: "when I eat
     * a bunch of food back up to full it starts immediately draining instead of being saturated for
     * a period of time").
     */
    public void applyRealHungerDrop(int amount) {
        int remaining = amount;
        if (trueSaturation > 0.0F) {
            int spent = (int) Math.min(trueSaturation, (float) remaining);
            trueSaturation -= spent;
            remaining -= spent;
        }
        if (remaining > 0) {
            trueHunger = Math.max(0, trueHunger - remaining);
        }
        trueSaturation = Mth.clamp(trueSaturation, 0.0F, (float) trueHunger);
    }

    /** Adds hunger/saturation from eating, capped at {@code currentMax} (Survivalist-derived). Mirrors vanilla's own {@code FoodData#add}. */
    public void eat(int nutrition, float saturationGained, int currentMax) {
        trueHunger = Mth.clamp(nutrition + trueHunger, 0, currentMax);
        trueSaturation = Mth.clamp(saturationGained + trueSaturation, 0.0F, (float) trueHunger);
    }

    /** Sets true hunger directly to {@code amount}, clamped to {@code [0, currentMax]} -- for debug tooling, not gameplay. */
    public void setTrueHunger(int amount, int currentMax) {
        trueHunger = Mth.clamp(amount, 0, currentMax);
        trueSaturation = Mth.clamp(trueSaturation, 0.0F, (float) trueHunger);
    }

    /** Spends saturation directly (the fast-regen tier "fuel"). Never below 0. */
    public void spendSaturation(float amount) {
        trueSaturation = Math.max(0.0F, trueSaturation - amount);
    }
}
