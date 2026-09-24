package com.github.cerealklla.lyfe.location;

import io.netty.buffer.ByteBuf;

import com.github.cerealklla.lyfe.LyfeMod;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server-to-client: the name of the place the receiving player currently believes they're in.
 * Moved here from Cartographyr 2026-09-24 (see decisions.md) — this is player *knowledge*, not
 * world truth, so it belongs to Lyfe even though the underlying place data still comes from
 * Cartographyr's read-only API.
 */
public record LocationPayload(String name) implements CustomPacketPayload {

    public static final Type<LocationPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(LyfeMod.MODID, "location"));

    public static final StreamCodec<ByteBuf, LocationPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.STRING_UTF8, LocationPayload::name, LocationPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
