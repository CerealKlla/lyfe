package com.github.cerealklla.lyfe.knowledge;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import io.netty.buffer.ByteBuf;

import com.github.cerealklla.lyfe.LyfeMod;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server-to-client: opens {@code WritingScreen} for a sign or map placement, carrying the
 * writer's own known-places list (server-precomputed, so the client makes no further Cartographyr
 * queries of its own -- design doc Section 9.1's "select a known place" mode).
 */
public record OpenWritingScreenPayload(WritingTarget target, List<KnownPlace> knownPlaces) implements CustomPacketPayload {

    /** @param embeddableFactors the writer's capped-and-known {@link KnowledgeFactor}s for this place -- see SignListener's writer-quality-cap helper. */
    public record KnownPlace(long entityId, String name, Set<KnowledgeFactor> embeddableFactors) {
        static final StreamCodec<ByteBuf, KnownPlace> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_LONG, KnownPlace::entityId,
                ByteBufCodecs.STRING_UTF8, KnownPlace::name,
                ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8.map(KnowledgeFactor::valueOf, Enum::name))
                        .map(Set::copyOf, ArrayList::new), KnownPlace::embeddableFactors,
                KnownPlace::new);
    }

    public static final Type<OpenWritingScreenPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(LyfeMod.MODID, "open_writing_screen"));

    public static final StreamCodec<ByteBuf, OpenWritingScreenPayload> STREAM_CODEC = StreamCodec.composite(
            WritingTarget.STREAM_CODEC, OpenWritingScreenPayload::target,
            ByteBufCodecs.collection(ArrayList::new, KnownPlace.STREAM_CODEC), OpenWritingScreenPayload::knownPlaces,
            OpenWritingScreenPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
