package com.github.cerealklla.lyfe.fishing;

import java.util.Optional;

import com.github.cerealklla.lyfe.LyfeMod;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server-to-the-casting-player-only: where this cast's "lucky spot" is, or empty to clear it
 * (design doc Section D). Never broadcast to anyone else -- the whole point is a bonus visible only
 * to the player who's actually fishing there, rendered client-side as a repeating particle effect
 * ({@code fishing.client.ClientLuckySpotState}), not a real networked entity.
 */
public record LuckySpotPayload(Optional<BlockPos> pos) implements CustomPacketPayload {

    public static final Type<LuckySpotPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(LyfeMod.MODID, "lucky_spot"));

    public static final StreamCodec<ByteBuf, LuckySpotPayload> STREAM_CODEC = ByteBufCodecs.optional(BlockPos.STREAM_CODEC)
            .map(LuckySpotPayload::new, LuckySpotPayload::pos);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
