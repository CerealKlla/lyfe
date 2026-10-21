package com.github.cerealklla.lyfe.summoning;

import io.netty.buffer.ByteBuf;

import com.github.cerealklla.lyfe.LyfeMod;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client-to-server: "Bind to Summoning Stone" clicked on the injected {@code HorseInventoryScreen} button -- see {@code LyfeModClient}. */
public record RequestMountBindPayload(int mountEntityId) implements CustomPacketPayload {

    public static final Type<RequestMountBindPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(LyfeMod.MODID, "request_mount_bind"));

    public static final StreamCodec<ByteBuf, RequestMountBindPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RequestMountBindPayload::mountEntityId,
            RequestMountBindPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
