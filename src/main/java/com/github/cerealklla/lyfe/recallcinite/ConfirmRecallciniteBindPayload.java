package com.github.cerealklla.lyfe.recallcinite;

import io.netty.buffer.ByteBuf;

import com.github.cerealklla.lyfe.LyfeMod;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client-to-server: the player clicked "Yes" on {@code RecallciniteBindConfirmScreen}. Empty --
 * the server re-reads the player's own current position at the moment this is handled, same
 * server-side-authoritative approach as every other confirm-and-act payload in this suite.
 */
public record ConfirmRecallciniteBindPayload() implements CustomPacketPayload {

    public static final Type<ConfirmRecallciniteBindPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(LyfeMod.MODID, "confirm_recallcinite_bind"));

    public static final StreamCodec<ByteBuf, ConfirmRecallciniteBindPayload> STREAM_CODEC =
            StreamCodec.unit(new ConfirmRecallciniteBindPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
