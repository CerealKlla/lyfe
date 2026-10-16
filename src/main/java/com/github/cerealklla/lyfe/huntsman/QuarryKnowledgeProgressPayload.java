package com.github.cerealklla.lyfe.huntsman;

import io.netty.buffer.ByteBuf;

import com.github.cerealklla.lyfe.LyfeMod;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server-to-client: {@code mobType}'s Quarry Knowledge kill count just changed for the receiving
 * player -- mirrors {@code research.ResearchProgressPayload} exactly (same reasoning: carries both
 * totals so the client can animate a fill without caching previous state itself). Triggers the
 * transient Quarry Knowledge HUD (see {@code huntsman.client.ClientHuntsmanBarState}), the same
 * visual style {@code research.client.ResearchBarOverlay} uses, per explicit user request.
 */
public record QuarryKnowledgeProgressPayload(Identifier mobType, int oldKills, int newKills) implements CustomPacketPayload {

    public static final Type<QuarryKnowledgeProgressPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(LyfeMod.MODID, "quarry_knowledge_progress"));

    public static final StreamCodec<ByteBuf, QuarryKnowledgeProgressPayload> STREAM_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, QuarryKnowledgeProgressPayload::mobType,
            ByteBufCodecs.VAR_INT, QuarryKnowledgeProgressPayload::oldKills,
            ByteBufCodecs.VAR_INT, QuarryKnowledgeProgressPayload::newKills,
            QuarryKnowledgeProgressPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
