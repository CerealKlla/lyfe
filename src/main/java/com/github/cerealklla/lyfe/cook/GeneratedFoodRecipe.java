package com.github.cerealklla.lyfe.cook;

import java.util.LinkedHashMap;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;

/**
 * One server-generated cooking recipe (design doc Section 19.3/19.4, extended to food 2026-10-03)
 * -- an unordered ingredient-id to quantity map, same "no 2D shape" shape as
 * {@code craft.GeneratedRecipe}, kept as its own distinct type since food has no {@code ToolType}-
 * style secondary axis and no generic-component-group feature (deliberate scope simplification --
 * see decisions.md).
 */
public record GeneratedFoodRecipe(Identifier resultId, int tier, Map<Identifier, Integer> components) {

    public static final Codec<GeneratedFoodRecipe> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("result_id").forGetter(GeneratedFoodRecipe::resultId),
            Codec.INT.fieldOf("tier").forGetter(GeneratedFoodRecipe::tier),
            Codec.unboundedMap(Identifier.CODEC, Codec.INT).fieldOf("components").forGetter(GeneratedFoodRecipe::components)
    ).apply(i, GeneratedFoodRecipe::new));

    public int slotsUsed() {
        return components.size();
    }

    /** Menu-open packet (extra-data) serialization -- see {@code CookingStructureBlockEntity#writeClientSideData}. */
    public static void writeTo(RegistryFriendlyByteBuf buffer, GeneratedFoodRecipe recipe) {
        Identifier.STREAM_CODEC.encode(buffer, recipe.resultId());
        buffer.writeVarInt(recipe.tier());
        buffer.writeVarInt(recipe.components().size());
        for (var entry : recipe.components().entrySet()) {
            Identifier.STREAM_CODEC.encode(buffer, entry.getKey());
            buffer.writeVarInt(entry.getValue());
        }
    }

    public static GeneratedFoodRecipe readFrom(RegistryFriendlyByteBuf buffer) {
        Identifier resultId = Identifier.STREAM_CODEC.decode(buffer);
        int tier = buffer.readVarInt();
        int count = buffer.readVarInt();
        Map<Identifier, Integer> components = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            Identifier id = Identifier.STREAM_CODEC.decode(buffer);
            int qty = buffer.readVarInt();
            components.put(id, qty);
        }
        return new GeneratedFoodRecipe(resultId, tier, components);
    }
}
