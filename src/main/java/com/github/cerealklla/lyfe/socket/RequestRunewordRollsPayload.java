package com.github.cerealklla.lyfe.socket;

import io.netty.buffer.ByteBuf;

import com.github.cerealklla.lyfe.LyfeMod;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client-to-server: sent once when the Rune Codex tab's "Runewords" sub-tab is first shown, asking
 * the server for every Runeword's real rolled {@link RuneId} sequence ({@link
 * RunewordRollsPayload}). Empty, same shape as {@code map.RequestMapDataPayload} -- the player is
 * already known server-side from the packet's own connection.
 */
public record RequestRunewordRollsPayload() implements CustomPacketPayload {

    public static final Type<RequestRunewordRollsPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(LyfeMod.MODID, "request_runeword_rolls"));

    public static final StreamCodec<ByteBuf, RequestRunewordRollsPayload> STREAM_CODEC =
            StreamCodec.unit(new RequestRunewordRollsPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
