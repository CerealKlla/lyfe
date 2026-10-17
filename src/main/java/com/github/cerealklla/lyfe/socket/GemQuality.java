package com.github.cerealklla.lyfe.socket;

import com.mojang.serialization.Codec;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** The 5 gem quality tiers (explicit user spec) -- {@link #index()} drives {@link GemCatalog}'s linear 5-step value scale. */
public enum GemQuality {
    CHIPPED("Chipped"), FLAWED("Flawed"), NORMAL("Normal"), FLAWLESS("Flawless"), PERFECT("Perfect");

    public static final Codec<GemQuality> CODEC = Codec.STRING.xmap(GemQuality::valueOf, Enum::name);
    public static final StreamCodec<RegistryFriendlyByteBuf, GemQuality> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    private final String displayName;

    GemQuality(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    /** 0 (Chipped) through 4 (Perfect) -- the step count {@link GemCatalog} multiplies its per-stat step by. */
    public int index() {
        return ordinal();
    }
}
