package com.github.cerealklla.lyfe.swim;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.util.Mth;

/**
 * A single player's true air state (design doc Section 5's Swimmer mechanic). Mirrors
 * {@code .hunger.PlayerHunger}'s role: the real source of truth for gameplay -- vanilla's own air
 * supply keeps running untouched underneath and is never itself the authority once this attachment
 * exists; see {@link SwimmerListener}.
 *
 * <p>{@code cachedXp} is the pending Swimmer XP bank (design doc: "cached rather than granted
 * immediately" while diving) -- paid out in full on surfacing, wiped entirely on drowning damage.
 */
public final class PlayerAir {

    public static final int SCHEMA_VERSION = 1;

    public static final MapCodec<PlayerAir> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.fieldOf("schema_version").forGetter(p -> p.schemaVersion),
            Codec.INT.fieldOf("true_air").forGetter(p -> p.trueAir),
            Codec.LONG.fieldOf("cached_xp").forGetter(p -> p.cachedXp)
    ).apply(i, PlayerAir::new));

    private final int schemaVersion;
    private int trueAir;
    private long cachedXp;

    public PlayerAir() {
        this(SCHEMA_VERSION, AirConstants.BASE_MAX_AIR, 0L);
    }

    private PlayerAir(int schemaVersion, int trueAir, long cachedXp) {
        this.schemaVersion = schemaVersion;
        this.trueAir = trueAir;
        this.cachedXp = cachedXp;
    }

    public int getTrueAir() {
        return trueAir;
    }

    public long getCachedXp() {
        return cachedXp;
    }

    /** Deducts a real, absolute tick loss (never scaled) observed from vanilla's own air supply. Never below 0. */
    public void applyRealAirDrop(int amount, int currentMax) {
        trueAir = Mth.clamp(trueAir - amount, 0, currentMax);
    }

    /** Recovers a real, absolute tick gain while breathing, at the Swimmer-scaled recovery rate. Never above currentMax. */
    public void applyRealAirGain(int amount, int currentMax) {
        trueAir = Mth.clamp(trueAir + amount, 0, currentMax);
    }

    public void bankXp(long amount) {
        cachedXp += amount;
    }

    /**
     * Spends up to {@code amount} from the bank (paid out as real XP by the caller), clamped to
     * what's actually there; returns the amount actually spent. Paying incrementally as bubbles
     * refill -- rather than the whole bank at once on surfacing -- means a dive interrupted before
     * air fully recovers (re-submerging, or drowning before the remaining bank is spent) only ever
     * pays for the bubbles that actually refilled (see {@link #wipeBank()} for the drowning case).
     */
    public long spendBank(long amount) {
        long spent = Math.min(cachedXp, amount);
        cachedXp -= spent;
        return spent;
    }

    /** Drowning damage wipes the bank instead of paying it out (design doc's explicit risk/reward rule). */
    public void wipeBank() {
        cachedXp = 0L;
    }
}
