package com.github.cerealklla.lyfe.craft;

import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.ByteBuf;

import com.github.cerealklla.lyfe.LyfeMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server-to-client: every currently-generated {@link GeneratedRecipe} ({@code
 * ServerRecipeStore#all}), sent once on request for the Crafting Guide tab's ingredient-cost
 * tooltip. Reuses {@link GeneratedRecipe#writeTo}/{@link GeneratedRecipe#readFrom} (the same
 * {@code RegistryFriendlyByteBuf} codec {@code CraftingStructureBlockEntity}'s menu-open extra
 * data already uses) via {@link StreamCodec#of} rather than duplicating that serialization.
 */
public record CraftingRecipesPayload(List<GeneratedRecipe> recipes) implements CustomPacketPayload {

    private static final StreamCodec<RegistryFriendlyByteBuf, GeneratedRecipe> RECIPE_STREAM_CODEC =
            StreamCodec.of(GeneratedRecipe::writeTo, GeneratedRecipe::readFrom);

    public static final Type<CraftingRecipesPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(LyfeMod.MODID, "crafting_recipes"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CraftingRecipesPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.collection(ArrayList::new, RECIPE_STREAM_CODEC), CraftingRecipesPayload::recipes,
            CraftingRecipesPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
