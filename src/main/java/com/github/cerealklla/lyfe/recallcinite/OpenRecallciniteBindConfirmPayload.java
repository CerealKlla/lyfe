package com.github.cerealklla.lyfe.recallcinite;

import io.netty.buffer.ByteBuf;

import com.github.cerealklla.lyfe.LyfeMod;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server-to-client: opens the Recallcinite Totem's Yes/No bind-confirmation screen -- sent directly
 * from a server-side interaction (the totem's own quick right-click tap), same as {@code
 * knowledge.OpenWritingScreenPayload}'s map case. Empty -- the client needs no extra data to render
 * the confirmation; the actual bind location/cooldown/Tier are all resolved server-side on confirm.
 */
public record OpenRecallciniteBindConfirmPayload() implements CustomPacketPayload {

    public static final Type<OpenRecallciniteBindConfirmPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(LyfeMod.MODID, "open_recallcinite_bind_confirm"));

    public static final StreamCodec<ByteBuf, OpenRecallciniteBindConfirmPayload> STREAM_CODEC =
            StreamCodec.unit(new OpenRecallciniteBindConfirmPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
