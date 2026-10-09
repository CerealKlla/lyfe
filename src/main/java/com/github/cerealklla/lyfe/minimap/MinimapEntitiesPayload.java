package com.github.cerealklla.lyfe.minimap;

import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.ByteBuf;

import com.github.cerealklla.lyfe.LyfeMod;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server-to-client: nearby Settlemynts plot/settlement outer-ring outlines, relative to the
 * player's own block position (small ints -- kept cheap over the network, unlike the minimap's
 * terrain, which never leaves the client at all, see {@link ClientTerrainSampler}). Sent by {@link
 * MinimapTracker}, a small sibling to {@code location.LocationTracker}, on the same throttled-tick
 * idea that class already uses.
 */
public record MinimapEntitiesPayload(List<Outline> outlines) implements CustomPacketPayload {

    /** Picks the outline color client-side (a fixed small palette -- see {@code MinimapOverlay#drawOutlines}, since Settlemynts' own per-zone-type wall color isn't reachable from Lyfe without a compile dependency). */
    public enum Kind {
        PLOT,
        SETTLEMENT_CORE,
        /** Cartographyr's naturally-discovered village footprint ({@code EntityType.SETTLEMENT}), added 2026-10-08. */
        NATURAL_SETTLEMENT,
        /** A natural village's auto-generated per-building plot ({@code settlemynts:natural_plot}) -- re-added to the minimap 2026-10-08 per explicit user request, as a debugging aid for the detection/zone-inference work. */
        NATURAL_PLOT;

        public static final StreamCodec<ByteBuf, Kind> STREAM_CODEC = ByteBufCodecs.idMapper(i -> values()[i], Kind::ordinal);
    }

    public record Outline(Kind kind, List<Integer> relativeX, List<Integer> relativeZ) {
        public static final StreamCodec<ByteBuf, Outline> STREAM_CODEC = StreamCodec.composite(
                Kind.STREAM_CODEC, Outline::kind,
                ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.VAR_INT), Outline::relativeX,
                ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.VAR_INT), Outline::relativeZ,
                Outline::new);
    }

    public static final Type<MinimapEntitiesPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(LyfeMod.MODID, "minimap_entities"));

    public static final StreamCodec<ByteBuf, MinimapEntitiesPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.collection(ArrayList::new, Outline.STREAM_CODEC), MinimapEntitiesPayload::outlines,
            MinimapEntitiesPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
