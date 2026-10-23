package com.github.cerealklla.lyfe.map.cache;

import com.github.cerealklla.lyfe.LyfeMod;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client-to-server, sent once on login: the real, raw {@code ServerData#ip} string the client
 * resolved for the server it just joined -- purely diagnostic, added 2026-10-23 alongside {@link
 * MapStorageKey}'s own address-normalization fix for a real report ("their Map keeps getting wiped
 * every time we do a server update"). Lets a server-side log comparison across sessions actually
 * confirm or rule out address-string drift as the cause, instead of guessing blind -- see {@code
 * LyfeMod}'s own payload registration for where it's logged.
 */
public record ReportServerAddressPayload(String rawAddress) implements CustomPacketPayload {

    public static final Type<ReportServerAddressPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(LyfeMod.MODID, "report_server_address"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, ReportServerAddressPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, ReportServerAddressPayload::rawAddress,
                    ReportServerAddressPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
