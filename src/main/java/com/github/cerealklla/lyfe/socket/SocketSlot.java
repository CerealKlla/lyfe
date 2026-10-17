package com.github.cerealklla.lyfe.socket;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * One socket on a socketed item -- either {@link #EMPTY} (drilled, nothing inserted yet), holding a
 * {@link GemType}+{@link GemQuality}, or holding a {@link RuneId}. A flat record with all-optional
 * fields (same "no sealed-interface Codec dispatch needed" shape {@code zone.ShopResource} already
 * uses in the Settlemynts suite) rather than a sealed interface -- simpler to serialize, and {@link
 * Kind} alone is enough to know which optional fields are meaningful.
 */
public record SocketSlot(Kind kind, Optional<GemType> gemType, Optional<GemQuality> gemQuality, Optional<RuneId> runeId) {

    public enum Kind { EMPTY, GEM, RUNE }

    public static final SocketSlot EMPTY = new SocketSlot(Kind.EMPTY, Optional.empty(), Optional.empty(), Optional.empty());

    public static SocketSlot ofGem(GemType type, GemQuality quality) {
        return new SocketSlot(Kind.GEM, Optional.of(type), Optional.of(quality), Optional.empty());
    }

    public static SocketSlot ofRune(RuneId id) {
        return new SocketSlot(Kind.RUNE, Optional.empty(), Optional.empty(), Optional.of(id));
    }

    public static final Codec<SocketSlot> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.xmap(Kind::valueOf, Enum::name).fieldOf("kind").forGetter(SocketSlot::kind),
            Codec.STRING.xmap(GemType::valueOf, Enum::name).optionalFieldOf("gem_type").forGetter(SocketSlot::gemType),
            Codec.STRING.xmap(GemQuality::valueOf, Enum::name).optionalFieldOf("gem_quality").forGetter(SocketSlot::gemQuality),
            Codec.STRING.xmap(RuneId::valueOf, Enum::name).optionalFieldOf("rune_id").forGetter(SocketSlot::runeId)
    ).apply(i, SocketSlot::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, SocketSlot> STREAM_CODEC =
            ByteBufCodecs.fromCodecWithRegistries(CODEC);
}
