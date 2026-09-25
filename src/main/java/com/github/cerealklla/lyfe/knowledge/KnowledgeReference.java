package com.github.cerealklla.lyfe.knowledge;

import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The data embedded in a placed Cartographyr sign or map (design doc Section 9.1) — shared by
 * both, backing a {@code SignBlockEntity} attachment (sign) and an item {@code
 * DataComponentType} (map). Every sign/map references a real known place (free text was removed
 * 2026-09-24, see decisions.md) — there is no longer a "no place" case to represent.
 *
 * <p>{@code entityId} is a raw {@code long} (Cartographyr's {@code EntityId#value()}), not
 * Cartographyr's {@code EntityId} type itself — this record backs {@code ModItems}'s {@code
 * KNOWLEDGE_REFERENCE} data component, which is registered unconditionally, so its {@code CODEC}
 * must not reference any Cartographyr class (same reasoning as {@link PlayerKnowledge}'s class
 * doc). Only {@code knowledge.SignListener} (Cartographyr-gated) ever constructs one from a real
 * {@code EntityId}.
 *
 * @param entityId the referenced place's raw id
 * @param embeddedPrecision the precision the writer embedded (capped by their own skill level and
 *                          their own knowledge of the place — see {@code SignListener}'s
 *                          writer-quality-cap helper)
 * @param displayText the place's fully-composed display text at write time (see {@code
 *                    geo.DisplayText#forEntity} — includes designation/ruin-prefix, not just the
 *                    bare name) — a snapshot, not a live lookup, so it doesn't update if the
 *                    place's name/designation/lifecycle state changes after this was written
 * @param writerId the writer's UUID (added 2026-09-25, see decisions.md) — so a reader upgrading
 *                 their knowledge from this sign/map can credit Cartographyr XP back to whoever
 *                 wrote it, even if they're offline at that moment
 */
public record KnowledgeReference(long entityId, LocationPrecision embeddedPrecision, String displayText, UUID writerId) {

    public static final Codec<KnowledgeReference> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("entity_id").forGetter(KnowledgeReference::entityId),
            LocationPrecision.CODEC.fieldOf("embedded_precision").forGetter(KnowledgeReference::embeddedPrecision),
            Codec.STRING.fieldOf("display_text").forGetter(KnowledgeReference::displayText),
            UUIDUtil.CODEC.fieldOf("writer_id").forGetter(KnowledgeReference::writerId)
    ).apply(i, KnowledgeReference::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, KnowledgeReference> STREAM_CODEC =
            ByteBufCodecs.fromCodecWithRegistries(CODEC);
}
