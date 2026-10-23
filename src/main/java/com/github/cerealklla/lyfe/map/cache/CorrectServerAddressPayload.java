package com.github.cerealklla.lyfe.map.cache;

import com.github.cerealklla.lyfe.LyfeMod;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server-to-client, sent only when a {@link ReportServerAddressPayload} genuinely disagrees (after
 * {@link ServerAddressNormalizer}) with this server's own canonical address -- carries the correct
 * address to use instead. The client stores it in {@link ClientServerAddressOverride} (always wins
 * over {@code ServerData#ip} from then on) and reloads the full Map's {@code TerrainCache} so it
 * reads back from the correct on-disk key immediately, rather than silently continuing to write new
 * exploration into the wrong (and possibly still-empty) folder for the rest of the session.
 */
public record CorrectServerAddressPayload(String correctAddress) implements CustomPacketPayload {

    public static final Type<CorrectServerAddressPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(LyfeMod.MODID, "correct_server_address"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, CorrectServerAddressPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, CorrectServerAddressPayload::correctAddress,
                    CorrectServerAddressPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
