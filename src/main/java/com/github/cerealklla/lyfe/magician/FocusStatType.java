package com.github.cerealklla.lyfe.magician;

import com.github.cerealklla.lyfe.craft.FocusLadder;
import com.mojang.serialization.Codec;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The 6 passive stats a Focus can roll (design doc Section 25, "Passive Stats pool"). Each entry's
 * range is given as T2's own fixed value and T5's own range, per the clarified spec: T2 always
 * rolls exactly its one number (zero variance), and the range widens linearly toward T5 for T3/T4
 * -- see {@link #minAt}/{@link #maxAt}.
 */
public enum FocusStatType {
    MAX_MANA("Max Mana", 10, 10, 50, 80),
    MANA_REGEN("Mana Regen", 1, 1, 3, 5),
    FASTER_CASTING_RATE("Faster Casting Rate", 5, 5, 8, 12),
    MAX_HEALTH("Max Health", 1, 1, 2, 3),
    MAGIC_FIND("Magic Find", 2, 5, 5, 10),
    GOLD_FIND("Gold Find", 5, 5, 5, 10);

    private final String label;
    private final double minAtT2;
    private final double maxAtT2;
    private final double minAtT5;
    private final double maxAtT5;

    FocusStatType(String label, double minAtT2, double maxAtT2, double minAtT5, double maxAtT5) {
        this.label = label;
        this.minAtT2 = minAtT2;
        this.maxAtT2 = maxAtT2;
        this.minAtT5 = minAtT5;
        this.maxAtT5 = maxAtT5;
    }

    public String label() {
        return label;
    }

    private double fractionFor(int tier) {
        int clamped = Math.max(FocusLadder.MIN_TIER, Math.min(tier, FocusLadder.MAX_TIER));
        return (clamped - FocusLadder.MIN_TIER) / (double) (FocusLadder.MAX_TIER - FocusLadder.MIN_TIER);
    }

    public double minAt(int tier) {
        return minAtT2 + (minAtT5 - minAtT2) * fractionFor(tier);
    }

    public double maxAt(int tier) {
        return maxAtT2 + (maxAtT5 - maxAtT2) * fractionFor(tier);
    }

    /** Rolls a whole-number value in this stat's tier-interpolated range (every stat's own unit is naturally whole -- mana points, mana/second, whole-percent, whole hearts). */
    public int roll(int tier, java.util.Random random) {
        int min = (int) Math.round(minAt(tier));
        int max = (int) Math.round(maxAt(tier));
        return min >= max ? min : min + random.nextInt(max - min + 1);
    }

    /** "+15 Max Mana", "+9% Faster Casting Rate", "+2 Max Health" -- real rolled value, matching this mod's "never formulas" tooltip convention. */
    public String formatted(double value) {
        boolean percent = this == FASTER_CASTING_RATE || this == MAGIC_FIND || this == GOLD_FIND;
        String amount = (int) value + (percent ? "%" : "");
        if (this == MANA_REGEN) {
            return "+" + amount + " Mana Regen/second";
        }
        if (this == MAX_HEALTH) {
            return "+" + amount + " Heart" + ((int) value == 1 ? "" : "s");
        }
        return "+" + amount + " " + label;
    }

    public static final Codec<FocusStatType> CODEC = Codec.STRING.xmap(FocusStatType::valueOf, Enum::name);
    public static final StreamCodec<RegistryFriendlyByteBuf, FocusStatType> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);
}
