package com.github.cerealklla.lyfe.recallcinite;

import io.netty.buffer.ByteBuf;

import com.github.cerealklla.lyfe.LyfeMod;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server-to-client: sent every tick while a player channels the Recallcinite Totem's 10-second
 * recall hold -- drives {@code client.RecallciniteChannelOverlay}'s progress bar. The client treats
 * a gap in these (no further packet for a few ticks) as "channel ended" rather than needing an
 * explicit stop message -- see that overlay's own doc.
 */
public record RecallciniteChannelProgressPayload(float fraction) implements CustomPacketPayload {

    public static final Type<RecallciniteChannelProgressPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(LyfeMod.MODID, "recallcinite_channel_progress"));

    public static final StreamCodec<ByteBuf, RecallciniteChannelProgressPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, RecallciniteChannelProgressPayload::fraction,
            RecallciniteChannelProgressPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
