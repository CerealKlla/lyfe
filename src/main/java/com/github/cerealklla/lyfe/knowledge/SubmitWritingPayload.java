package com.github.cerealklla.lyfe.knowledge;

import java.util.Optional;

import io.netty.buffer.ByteBuf;

import com.github.cerealklla.lyfe.LyfeMod;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client-to-server: submits a {@code WritingScreen} dialog -- exactly one of {@code freeText} or
 * {@code chosenPlaceId} is present, never both (design doc Section 9.1's two modes).
 */
public record SubmitWritingPayload(WritingTarget target, Optional<String> freeText, Optional<Long> chosenPlaceId) implements CustomPacketPayload {

    public static final Type<SubmitWritingPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(LyfeMod.MODID, "submit_writing"));

    public static final StreamCodec<ByteBuf, SubmitWritingPayload> STREAM_CODEC = StreamCodec.composite(
            WritingTarget.STREAM_CODEC, SubmitWritingPayload::target,
            ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8), SubmitWritingPayload::freeText,
            ByteBufCodecs.optional(ByteBufCodecs.VAR_LONG), SubmitWritingPayload::chosenPlaceId,
            SubmitWritingPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
