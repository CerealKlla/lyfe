package com.github.cerealklla.lyfe.socket;

import com.mojang.serialization.Codec;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The 30 fixed rune names (explicit user spec: "2-4 letters... follow 'English' word conventions...
 * these won't be dynamic"). Originally authored, not reused from any existing rune set. Each carries
 * one fixed minor universal benefit (see {@link RuneCatalog}) and can appear in a {@link
 * RunewordDefinition}'s server-randomized rune sequence (see {@link ServerRunewordStore}).
 */
public enum RuneId {
    ZAR, ITH, VEX, LORN, KEL, THAL, ORIN, MYR, FEN, DASK,
    WYN, HALE, BROM, GLEN, KORR, SAEL, NYM, PYRE, DOTH, QUIN,
    RASK, ESH, VORN, TILN, ASHK, OLM, DREV, SORN, KAEL, RYTH;

    public static final Codec<RuneId> CODEC = Codec.STRING.xmap(RuneId::valueOf, Enum::name);
    public static final StreamCodec<RegistryFriendlyByteBuf, RuneId> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    public String displayName() {
        String n = name();
        return n.charAt(0) + n.substring(1).toLowerCase(java.util.Locale.ROOT);
    }
}
