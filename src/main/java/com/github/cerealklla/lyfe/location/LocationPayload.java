package com.github.cerealklla.lyfe.location;

import java.util.Optional;

import io.netty.buffer.ByteBuf;

import com.github.cerealklla.lyfe.LyfeMod;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server-to-client: the player's current location, as a fixed two-line HUD (design doc Section
 * 9.3-adjacent; see decisions.md, 2026-09-26). Moved here from Cartographyr 2026-09-24 -- this is
 * player *knowledge*, not world truth, so it belongs to Lyfe even though the underlying place data
 * still comes from Cartographyr's read-only API.
 *
 * <p><b>Reworked from a generic per-layer line list to this fixed 2-line format, 2026-09-26</b>
 * (explicit user request, replacing the earlier "one line per Layer" model) -- {@code line1} is
 * the resolved place's display name (a settlement if one's registered here, otherwise the
 * surrounding natural region, otherwise absent); {@code line2} is one of "Town Proper"/"No Man's
 * Land"/"Outskirts"/a plot's own name (or "Residence"), or absent if neither applies -- see {@code
 * LocationTracker#resolve} for the full resolution order.
 */
public record LocationPayload(Optional<String> line1, Optional<String> line2) implements CustomPacketPayload {

    public static final Type<LocationPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(LyfeMod.MODID, "location"));

    public static final StreamCodec<ByteBuf, LocationPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8), LocationPayload::line1,
            ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8), LocationPayload::line2,
            LocationPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
