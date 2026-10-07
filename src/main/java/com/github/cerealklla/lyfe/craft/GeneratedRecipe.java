package com.github.cerealklla.lyfe.craft;

import java.util.LinkedHashMap;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;

/**
 * One server-generated recipe (design doc Section 19.4): an unordered ingredient-id to quantity
 * map, no 2D shape/pattern at all -- confirmed with the user 2026-10-02 that a spatial arrangement
 * is unnecessary since the recipe is never rendered as a grid.
 *
 * <p>Split into two component maps 2026-10-03 (explicit user request) -- {@code specificComponents}
 * pins an exact concrete item (e.g. a tiered material like {@code iron_ingot}, where a substitute
 * would be a real power-level difference), while {@code genericComponents} names a
 * {@link ComponentGroups} group (e.g. {@code "Any Log"}) satisfiable by any of that group's
 * interchangeable member items -- fixes a real accessibility gap where a hardcoded {@code oak_log}
 * requirement couldn't be fulfilled by a player with only spruce nearby.
 */
public record GeneratedRecipe(
        Identifier resultId,
        int tier,
        Map<Identifier, Integer> specificComponents,
        Map<String, Integer> genericComponents) {

    public static final Codec<GeneratedRecipe> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("result_id").forGetter(GeneratedRecipe::resultId),
            Codec.INT.fieldOf("tier").forGetter(GeneratedRecipe::tier),
            Codec.unboundedMap(Identifier.CODEC, Codec.INT).fieldOf("specific_components").forGetter(GeneratedRecipe::specificComponents),
            Codec.unboundedMap(Codec.STRING, Codec.INT).fieldOf("generic_components").forGetter(GeneratedRecipe::genericComponents)
    ).apply(i, GeneratedRecipe::new));

    /** Total ingredient-slot count this recipe actually uses -- sum of every component's quantity count (one count per distinct ingredient, not per unit). */
    public int slotsUsed() {
        return specificComponents.size() + genericComponents.size();
    }

    /** Menu-open packet (extra-data) serialization -- see {@code CraftingStructureBlockEntity#writeClientSideData}. */
    public static void writeTo(RegistryFriendlyByteBuf buffer, GeneratedRecipe recipe) {
        Identifier.STREAM_CODEC.encode(buffer, recipe.resultId());
        buffer.writeVarInt(recipe.tier());
        buffer.writeVarInt(recipe.specificComponents().size());
        for (var entry : recipe.specificComponents().entrySet()) {
            Identifier.STREAM_CODEC.encode(buffer, entry.getKey());
            buffer.writeVarInt(entry.getValue());
        }
        buffer.writeVarInt(recipe.genericComponents().size());
        for (var entry : recipe.genericComponents().entrySet()) {
            buffer.writeUtf(entry.getKey());
            buffer.writeVarInt(entry.getValue());
        }
    }

    public static GeneratedRecipe readFrom(RegistryFriendlyByteBuf buffer) {
        Identifier resultId = Identifier.STREAM_CODEC.decode(buffer);
        int tier = buffer.readVarInt();
        int specificCount = buffer.readVarInt();
        Map<Identifier, Integer> specificComponents = new LinkedHashMap<>();
        for (int i = 0; i < specificCount; i++) {
            Identifier id = Identifier.STREAM_CODEC.decode(buffer);
            int qty = buffer.readVarInt();
            specificComponents.put(id, qty);
        }
        int genericCount = buffer.readVarInt();
        Map<String, Integer> genericComponents = new LinkedHashMap<>();
        for (int i = 0; i < genericCount; i++) {
            String groupName = buffer.readUtf();
            int qty = buffer.readVarInt();
            genericComponents.put(groupName, qty);
        }
        return new GeneratedRecipe(resultId, tier, specificComponents, genericComponents);
    }
}
