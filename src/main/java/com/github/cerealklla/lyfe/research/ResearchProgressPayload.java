package com.github.cerealklla.lyfe.research;

import io.netty.buffer.ByteBuf;

import com.github.cerealklla.lyfe.LyfeMod;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server-to-client: Research Points toward {@code resultId} just changed for the receiving player
 * (2026-10-03, user request -- "I'd like to see an XP Bar for the Research Points gained by
 * researching"). Carries both totals (not just the delta) so the client can animate a fill without
 * caching previous state itself -- same shape/reasoning as {@code xpbar.XpGainPayload}. Triggers
 * the transient research-progress HUD (see {@code research.client.ClientResearchBarState}).
 */
public record ResearchProgressPayload(Identifier resultId, int oldPoints, int newPoints, int threshold) implements CustomPacketPayload {

    public static final Type<ResearchProgressPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(LyfeMod.MODID, "research_progress"));

    public static final StreamCodec<ByteBuf, ResearchProgressPayload> STREAM_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, ResearchProgressPayload::resultId,
            ByteBufCodecs.VAR_INT, ResearchProgressPayload::oldPoints,
            ByteBufCodecs.VAR_INT, ResearchProgressPayload::newPoints,
            ByteBufCodecs.VAR_INT, ResearchProgressPayload::threshold,
            ResearchProgressPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
