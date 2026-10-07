package com.github.cerealklla.lyfe.cook;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

/**
 * Which cooking recipe a "Recipe Notes" item ({@code RecipeNoteItem}) is tied to -- direct mirror of
 * {@code research.ResearchNoteTarget}, kept as its own distinct component/item so Recipe Notes have
 * a separate dynamic display name/icon from equipment Research Notes.
 */
public record RecipeNoteTarget(Identifier resultId, int tier) {

    public static final Codec<RecipeNoteTarget> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("result_id").forGetter(RecipeNoteTarget::resultId),
            Codec.INT.fieldOf("tier").forGetter(RecipeNoteTarget::tier)
    ).apply(i, RecipeNoteTarget::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, RecipeNoteTarget> STREAM_CODEC =
            ByteBufCodecs.fromCodecWithRegistries(CODEC);
}
