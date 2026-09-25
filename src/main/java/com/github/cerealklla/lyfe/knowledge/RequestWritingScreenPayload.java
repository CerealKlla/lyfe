package com.github.cerealklla.lyfe.knowledge;

import io.netty.buffer.ByteBuf;

import com.github.cerealklla.lyfe.LyfeMod;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client-to-server: asks the server to compute the writer's known-places list and reply with
 * {@link OpenWritingScreenPayload} for {@code target} -- used only for the sign case, when the
 * player clicks the "Cartographyr" button injected into vanilla's own {@code SignEditScreen} (see
 * {@code LyfeModClient}). The map case doesn't need this round trip since it's triggered directly
 * from a server-side interaction event ({@code SignListener#onRightClickItem}).
 */
public record RequestWritingScreenPayload(WritingTarget target) implements CustomPacketPayload {

    public static final Type<RequestWritingScreenPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(LyfeMod.MODID, "request_writing_screen"));

    public static final StreamCodec<ByteBuf, RequestWritingScreenPayload> STREAM_CODEC = StreamCodec.composite(
            WritingTarget.STREAM_CODEC, RequestWritingScreenPayload::target,
            RequestWritingScreenPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
