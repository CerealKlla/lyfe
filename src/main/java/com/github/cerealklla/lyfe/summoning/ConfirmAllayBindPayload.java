package com.github.cerealklla.lyfe.summoning;

import io.netty.buffer.ByteBuf;

import com.github.cerealklla.lyfe.LyfeMod;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client-to-server: "Bind" clicked on {@code client.AllayBindNameScreen}. */
public record ConfirmAllayBindPayload(int allayEntityId, String name) implements CustomPacketPayload {

    public static final Type<ConfirmAllayBindPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(LyfeMod.MODID, "confirm_allay_bind"));

    public static final StreamCodec<ByteBuf, ConfirmAllayBindPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ConfirmAllayBindPayload::allayEntityId,
            ByteBufCodecs.STRING_UTF8, ConfirmAllayBindPayload::name,
            ConfirmAllayBindPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
