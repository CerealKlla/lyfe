package com.github.cerealklla.lyfe.knowledge;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The data embedded in a placed Cartographyr sign or map (design doc Section 9.1). {@code
 * entityId}/{@code embeddedPrecision} are present together for a "known place" reference, and
 * both empty for free-typed text -- free text ties to no place, so it can never grant XP or a
 * knowledge-store change on read (the anti-farming rule).
 *
 * <p>{@code entityId} is a raw {@code long} (Cartographyr's {@code EntityId#value()}), not
 * Cartographyr's {@code EntityId} type itself -- this record backs {@code ModItems}' {@code
 * KNOWLEDGE_REFERENCE} data component, which is registered unconditionally, so its {@code CODEC}
 * must not reference any Cartographyr class (same reasoning as {@link PlayerKnowledge}'s class
 * doc). Only {@code knowledge.SignListener} (Cartographyr-gated) ever constructs one from a real
 * {@code EntityId}.
 *
 * @param entityId the referenced place's raw id, if this is a known-place reference rather than free text
 * @param embeddedPrecision the precision the writer embedded (capped by their own skill level and
 *                          their own knowledge of the place -- see {@code SignListener}'s
 *                          writer-quality-cap helper); meaningless when {@code entityId} is empty
 * @param displayText what actually shows on the sign/map -- either the free text, or the
 *                     referenced place's name at write time (names can change later; this is a
 *                     snapshot, not a live lookup)
 */
public record KnowledgeReference(Optional<Long> entityId, Optional<LocationPrecision> embeddedPrecision, String displayText) {

    public static final Codec<KnowledgeReference> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.optionalFieldOf("entity_id").forGetter(KnowledgeReference::entityId),
            LocationPrecision.CODEC.optionalFieldOf("embedded_precision").forGetter(KnowledgeReference::embeddedPrecision),
            Codec.STRING.fieldOf("display_text").forGetter(KnowledgeReference::displayText)
    ).apply(i, KnowledgeReference::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, KnowledgeReference> STREAM_CODEC =
            ByteBufCodecs.fromCodecWithRegistries(CODEC);

    public static KnowledgeReference freeText(String text) {
        return new KnowledgeReference(Optional.empty(), Optional.empty(), text);
    }

    public static KnowledgeReference knownPlace(long entityId, LocationPrecision precision, String name) {
        return new KnowledgeReference(Optional.of(entityId), Optional.of(precision), name);
    }
}
