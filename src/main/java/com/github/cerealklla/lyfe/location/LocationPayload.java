package com.github.cerealklla.lyfe.location;

import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.ByteBuf;

import com.github.cerealklla.lyfe.LyfeMod;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server-to-client: one line per layer the receiving player currently believes they're in,
 * already sorted by placement (highest first) — the client just renders them in order. Moved here
 * from Cartographyr 2026-09-24 (see decisions.md) — this is player *knowledge*, not world truth, so
 * it belongs to Lyfe even though the underlying place data still comes from Cartographyr's
 * read-only API.
 *
 * <p>Reshaped from a single {@code String} to a list of {@link LocationLine}s the same day
 * (second pass), when Cartographyr's new open {@code Layer} registry made "more than one line" a
 * real possibility (e.g. a future Territory mod's claim shown above the base Location line).
 */
public record LocationPayload(List<LocationLine> lines) implements CustomPacketPayload {

    /** @param label the layer's display label (e.g. "Location", "Territory") */
    public record LocationLine(String label, String name) {
        static final StreamCodec<ByteBuf, LocationLine> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, LocationLine::label,
                ByteBufCodecs.STRING_UTF8, LocationLine::name,
                LocationLine::new);
    }

    public static final Type<LocationPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(LyfeMod.MODID, "location"));

    public static final StreamCodec<ByteBuf, LocationPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.collection(ArrayList::new, LocationLine.STREAM_CODEC), LocationPayload::lines,
            LocationPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
