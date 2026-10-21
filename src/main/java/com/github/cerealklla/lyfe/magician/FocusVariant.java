package com.github.cerealklla.lyfe.magician;

import com.mojang.serialization.Codec;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The 3 visual styles a Focus can roll (design doc Section 25, cosmetic-only, chosen randomly at
 * craft time) -- same {@code CustomModelData}-select-model mechanism as {@code socket.RuneId}'s
 * own icon selection.
 */
public enum FocusVariant {
    CANDLE("Candle"), SKULL("Skull"), OLD_BOOK("Old Book");

    private final String displayName;

    FocusVariant(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public static final Codec<FocusVariant> CODEC = Codec.STRING.xmap(FocusVariant::valueOf, Enum::name);
    public static final StreamCodec<RegistryFriendlyByteBuf, FocusVariant> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);
}
