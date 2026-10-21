package com.github.cerealklla.lyfe.summoning;

import io.netty.buffer.ByteBuf;

import com.github.cerealklla.lyfe.LyfeMod;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client-to-server: "Bind" clicked on {@code client.MountBindNameScreen}. */
public record ConfirmMountBindPayload(int mountEntityId, String name) implements CustomPacketPayload {

    public static final Type<ConfirmMountBindPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(LyfeMod.MODID, "confirm_mount_bind"));

    public static final StreamCodec<ByteBuf, ConfirmMountBindPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ConfirmMountBindPayload::mountEntityId,
            ByteBufCodecs.STRING_UTF8, ConfirmMountBindPayload::name,
            ConfirmMountBindPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
