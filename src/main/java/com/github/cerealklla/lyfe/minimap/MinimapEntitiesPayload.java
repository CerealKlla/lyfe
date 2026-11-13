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
 * Server-to-client: nearby Settlemynts plot/settlement outer-ring outlines, as absolute world
 * block coordinates. Sent by {@link MinimapTracker}, a small sibling to {@code
 * location.LocationTracker}, on the same throttled-tick idea that class already uses.
 *
 * <p>Switched from player-relative coordinates to absolute ones, 2026-11-13 -- real user report
 * ("plot boundaries are still not locked to the terrain... weird shifting as you walk around"),
 * which survived even after syncing this payload's own refresh cadence with the terrain texture's.
 * Real root cause: {@code MinimapOverlay#drawOutlines} drew each vertex at its offset as baked in
 * *at send time*, with no live correction for how far the player has moved since -- unlike the
 * terrain texture, which recomputes a drift-compensating shift against the player's live position
 * every single frame (see {@code drawTerrainTexture}'s own {@code shiftX}/{@code shiftZ}). Plot
 * boundaries don't move; only the player does, so storing/sending absolute coordinates and letting
 * the client subtract its own live position at draw time (every frame, never stale) is the correct
 * fix, not just resyncing more often. The resulting coordinates are large real-world block positions
 * rather than small deltas, costing a little more over the wire per vertex -- negligible at this
 * payload's size and send frequency.
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

    /** @param worldX/@param worldZ absolute world block coordinates -- see this record's own class doc for why. */
    public record Outline(Kind kind, List<Integer> worldX, List<Integer> worldZ) {
        public static final StreamCodec<ByteBuf, Outline> STREAM_CODEC = StreamCodec.composite(
                Kind.STREAM_CODEC, Outline::kind,
                ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.VAR_INT), Outline::worldX,
                ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.VAR_INT), Outline::worldZ,
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
