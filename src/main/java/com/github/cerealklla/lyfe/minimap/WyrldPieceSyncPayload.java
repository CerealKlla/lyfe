package com.github.cerealklla.lyfe.minimap;

import io.netty.buffer.ByteBuf;

import com.github.cerealklla.lyfe.LyfeMod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server-to-client: which real saved piece (room/connector/entrance/Tunnel piece) the player is
 * currently standing inside, while in TinyWyrlds' Wyrld Space dimension -- built for the minimap's
 * room-scoped view there (explicit user request, 2026-11-12). Sent by {@link WyrldPieceTracker},
 * same throttled-tick shape as {@link MinimapTracker}. {@code present=false} means the player
 * isn't inside any single piece's own footprint right now (e.g. a connecting corridor cell, or not
 * in a Wyrld at all) -- {@code min}/{@code maxExclusive} are meaningless in that case, not
 * {@code Optional}-wrapped purely to keep the stream codec simple.
 */
public record WyrldPieceSyncPayload(boolean present, BlockPos min, BlockPos maxExclusive) implements CustomPacketPayload {

    public static final Type<WyrldPieceSyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(LyfeMod.MODID, "wyrld_piece_sync"));

    public static final StreamCodec<ByteBuf, WyrldPieceSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, WyrldPieceSyncPayload::present,
            BlockPos.STREAM_CODEC, WyrldPieceSyncPayload::min,
            BlockPos.STREAM_CODEC, WyrldPieceSyncPayload::maxExclusive,
            WyrldPieceSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
