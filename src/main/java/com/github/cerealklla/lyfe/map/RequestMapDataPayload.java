package com.github.cerealklla.lyfe.map;

import io.netty.buffer.ByteBuf;

import com.github.cerealklla.lyfe.LyfeMod;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client-to-server: sent once when {@code map.client.MapScreen} opens, asking the server to resolve
 * the player's known settlements into {@link MapSettlementsPayload}. Empty -- the player is already
 * known server-side from the packet's own connection.
 */
public record RequestMapDataPayload() implements CustomPacketPayload {

    public static final Type<RequestMapDataPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(LyfeMod.MODID, "request_map_data"));

    public static final StreamCodec<ByteBuf, RequestMapDataPayload> STREAM_CODEC =
            StreamCodec.unit(new RequestMapDataPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
