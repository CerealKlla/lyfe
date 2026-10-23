package com.github.cerealklla.lyfe.summoning;

import io.netty.buffer.ByteBuf;

import com.github.cerealklla.lyfe.LyfeMod;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server-to-client: opens the naming screen after a validated Allay-bind interaction -- see {@code AllayBindListener#requestBind}. */
public record OpenAllayBindNameScreenPayload(int allayEntityId) implements CustomPacketPayload {

    public static final Type<OpenAllayBindNameScreenPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(LyfeMod.MODID, "open_allay_bind_name_screen"));

    public static final StreamCodec<ByteBuf, OpenAllayBindNameScreenPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, OpenAllayBindNameScreenPayload::allayEntityId,
            OpenAllayBindNameScreenPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
