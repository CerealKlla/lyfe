package com.github.cerealklla.lyfe.research;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

/**
 * Which recipe a "Research Notes" item (see {@code ResearchNoteItem}) is tied to -- baked in at
 * creation time by {@code loot.ResearchNoteLootInjector}. {@code tier} is carried directly rather
 * than re-derived from {@code ServerRecipeStore} at display time: tier is static per item identity
 * ({@code EquipmentTierLadder} fixes which material tier each tool is), but {@code ServerRecipeStore}
 * is server-only, so baking it in keeps {@code ResearchNoteItem#getName} safe to call client-side.
 */
public record ResearchNoteTarget(Identifier resultId, int tier) {

    public static final Codec<ResearchNoteTarget> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("result_id").forGetter(ResearchNoteTarget::resultId),
            Codec.INT.fieldOf("tier").forGetter(ResearchNoteTarget::tier)
    ).apply(i, ResearchNoteTarget::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, ResearchNoteTarget> STREAM_CODEC =
            ByteBufCodecs.fromCodecWithRegistries(CODEC);
}
