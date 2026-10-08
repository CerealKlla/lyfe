package com.github.cerealklla.lyfe.map;

import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.ByteBuf;

import com.github.cerealklla.lyfe.LyfeMod;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server-to-client: every settlement the player has personal {@code knowledge.PlayerKnowledge} of
 * (never a live world scan -- see {@code map.MapDataListener}'s own doc for why that's exactly the
 * "only show places the player has been" behavior the original spec asked for), as absolute block
 * x/z plus already-resolved {@code Cartography.getDisplayText} strings. Absolute coordinates, unlike
 * {@code minimap.MinimapEntitiesPayload}'s player-relative ints -- the Map can cover hundreds of
 * blocks and isn't resent every tick, so there's no need to keep this small the way the
 * every-tick-eligible minimap payload does.
 */
public record MapSettlementsPayload(List<Entry> settlements) implements CustomPacketPayload {

    public record Entry(int x, int z, String displayText) {
        public static final StreamCodec<ByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Entry::x,
                ByteBufCodecs.VAR_INT, Entry::z,
                ByteBufCodecs.STRING_UTF8, Entry::displayText,
                Entry::new);
    }

    public static final Type<MapSettlementsPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(LyfeMod.MODID, "map_settlements"));

    public static final StreamCodec<ByteBuf, MapSettlementsPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.collection(ArrayList::new, Entry.STREAM_CODEC), MapSettlementsPayload::settlements,
            MapSettlementsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
