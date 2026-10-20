package com.github.cerealklla.lyfe.mana;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.util.Mth;

/**
 * One entity's current Mana (design doc, 2026-10-20 user spec: "Every Player/NPC has a base
 * maxMana = 50"). Attached to any {@code LivingEntity} -- player or NPC -- same entity-not-just-
 * player scoping as {@code combat.AssistMarkers}, since both players and Settlemynts' custom NPCs
 * need to spend/regen Mana to cast a spell. Mirrors {@code swim.PlayerAir}'s shape: only the real,
 * depletable resource lives here as mutable state; the live max (from {@code
 * registration.ModAttributes#MAX_MANA}, grown by Magician level + Nature Staff tier + Crafting
 * Quality) and regen rate (from {@code #MANA_REGEN}) are read live by the caller and passed in,
 * never cached here -- same reason {@code PlayerAir#applyRealAirDrop}/{@code #applyRealAirGain}
 * take {@code currentMax} as a parameter instead of storing it.
 */
public final class EntityMana {

    public static final int SCHEMA_VERSION = 1;

    public static final MapCodec<EntityMana> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.fieldOf("schema_version").forGetter(m -> m.schemaVersion),
            Codec.DOUBLE.fieldOf("current_mana").forGetter(m -> m.currentMana)
    ).apply(i, EntityMana::new));

    private final int schemaVersion;
    private double currentMana;

    public EntityMana() {
        this(SCHEMA_VERSION, 50.0);
    }

    private EntityMana(int schemaVersion, double currentMana) {
        this.schemaVersion = schemaVersion;
        this.currentMana = currentMana;
    }

    public double getCurrentMana() {
        return currentMana;
    }

    /** Clamps down to {@code currentMax} too, in case a buff wore off (staff unequipped, level lost somehow) and left this stale. */
    public void clampTo(double currentMax) {
        currentMana = Mth.clamp(currentMana, 0.0, currentMax);
    }

    /** Spends {@code amount}, only if at least that much is available. Returns whether the spend succeeded. */
    public boolean spend(double amount) {
        if (currentMana < amount) {
            return false;
        }
        currentMana -= amount;
        return true;
    }

    /** Regenerates up to {@code amount}, never past {@code currentMax}. */
    public void regen(double amount, double currentMax) {
        currentMana = Mth.clamp(currentMana + amount, 0.0, currentMax);
    }

    /** Instantly refills to {@code currentMax} -- used on first-ever access for a freshly-created entity/attachment. */
    public void fillTo(double currentMax) {
        currentMana = currentMax;
    }
}
