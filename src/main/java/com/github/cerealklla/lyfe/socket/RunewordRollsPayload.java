package com.github.cerealklla.lyfe.socket;

import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.ByteBuf;

import com.github.cerealklla.lyfe.LyfeMod;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server-to-client: every {@link RunewordCatalog} definition's real rolled {@link RuneId} sequence
 * ({@code ServerRunewordStore#sequenceFor}), sent once on request. The fixed definitions themselves
 * (name/category/effect/description) need no sync at all -- {@link RunewordCatalog} is identical,
 * static data on both physical sides already; only the server-rolled sequence is genuinely
 * server-specific. Plain {@code RuneId} enum names over the wire (not {@link RuneId#STREAM_CODEC},
 * which needs a registry-aware buffer this simple payload has no reason to require).
 */
public record RunewordRollsPayload(List<Entry> rolls) implements CustomPacketPayload {

    private static final StreamCodec<ByteBuf, RuneId> RUNE_ID_STREAM_CODEC =
            ByteBufCodecs.STRING_UTF8.map(RuneId::valueOf, Enum::name);

    public record Entry(String name, List<RuneId> sequence) {
        public static final StreamCodec<ByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Entry::name,
                ByteBufCodecs.collection(ArrayList::new, RUNE_ID_STREAM_CODEC), Entry::sequence,
                Entry::new);
    }

    public static final Type<RunewordRollsPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(LyfeMod.MODID, "runeword_rolls"));

    public static final StreamCodec<ByteBuf, RunewordRollsPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.collection(ArrayList::new, Entry.STREAM_CODEC), RunewordRollsPayload::rolls,
            RunewordRollsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
