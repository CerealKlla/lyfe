package com.github.cerealklla.lyfe.knowledge;

import io.netty.buffer.ByteBuf;

import com.github.cerealklla.lyfe.LyfeMod;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client-to-server: submits a {@code WritingScreen} dialog with the chosen known place. Free
 * text was removed 2026-09-24 (see decisions.md) — every sign/map references a real place now.
 */
public record SubmitWritingPayload(WritingTarget target, long chosenPlaceId) implements CustomPacketPayload {

    public static final Type<SubmitWritingPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(LyfeMod.MODID, "submit_writing"));

    public static final StreamCodec<ByteBuf, SubmitWritingPayload> STREAM_CODEC = StreamCodec.composite(
            WritingTarget.STREAM_CODEC, SubmitWritingPayload::target,
            ByteBufCodecs.VAR_LONG, SubmitWritingPayload::chosenPlaceId,
            SubmitWritingPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
