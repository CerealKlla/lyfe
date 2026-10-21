package com.github.cerealklla.lyfe.craft;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

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
 *
 * <p>{@code outputCount}/{@code outputCountMax} added 2026-10-21 for {@code MiscRecipes}' Cobblestone
 * -> Flint/Gravel conversion (explicit user spec: "5 cobblestone becomes 1-3 flint and 4 gravel") --
 * when {@code outputCountMax > outputCount}, {@code CraftingStructureMenu#attemptCraft} rolls a
 * uniform random amount in that inclusive range instead of using a fixed count; every existing
 * recipe sets both to the same value (a fixed amount, unchanged behavior). {@code secondaryResultId}/
 * {@code secondaryOutputCount} are an optional second, always-fixed-quantity output alongside the
 * primary one -- deliberately NOT a second randomized range or a list of arbitrarily many outputs,
 * since this one real recipe is the only thing that needs more than one output item at all; a
 * genuine need for more would be the time to generalize this into a real list.
 */
public record GeneratedRecipe(
        Identifier resultId,
        int tier,
        Map<Identifier, Integer> specificComponents,
        Map<String, Integer> genericComponents,
        int outputCount,
        int outputCountMax,
        Optional<Identifier> secondaryResultId,
        int secondaryOutputCount) {

    // Convenience constructor for every existing equipment recipe (tool/armor/ranged weapon/socket
    // tool), all of which produce exactly 1 of resultId -- keeps every call site in RecipeGenerator
    // unchanged instead of threading a literal "1" through each one.
    public GeneratedRecipe(Identifier resultId, int tier, Map<Identifier, Integer> specificComponents, Map<String, Integer> genericComponents) {
        this(resultId, tier, specificComponents, genericComponents, 1);
    }

    // Fixed single output, no range, no secondary -- MiscRecipes' original Leaf Litter -> Charcoal shape.
    public GeneratedRecipe(Identifier resultId, int tier, Map<Identifier, Integer> specificComponents, Map<String, Integer> genericComponents, int outputCount) {
        this(resultId, tier, specificComponents, genericComponents, outputCount, outputCount, Optional.empty(), 0);
    }

    public static final Codec<GeneratedRecipe> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("result_id").forGetter(GeneratedRecipe::resultId),
            Codec.INT.fieldOf("tier").forGetter(GeneratedRecipe::tier),
            Codec.unboundedMap(Identifier.CODEC, Codec.INT).fieldOf("specific_components").forGetter(GeneratedRecipe::specificComponents),
            Codec.unboundedMap(Codec.STRING, Codec.INT).fieldOf("generic_components").forGetter(GeneratedRecipe::genericComponents),
            // Added 2026-10-18 for MiscRecipes' fixed, non-equipment conversions (e.g. Leaf Litter ->
            // Charcoal) -- optionalFieldOf defaults to 1 so every already-persisted equipment recipe
            // (which never set this field at all) decodes unchanged.
            Codec.INT.optionalFieldOf("output_count", 1).forGetter(GeneratedRecipe::outputCount),
            // Added 2026-10-21; defaults to 1 so every already-persisted recipe (fixed output, no
            // range) decodes with outputCountMax == outputCount's own default, i.e. still fixed.
            Codec.INT.optionalFieldOf("output_count_max", 1).forGetter(GeneratedRecipe::outputCountMax),
            Identifier.CODEC.optionalFieldOf("secondary_result_id").forGetter(GeneratedRecipe::secondaryResultId),
            Codec.INT.optionalFieldOf("secondary_output_count", 0).forGetter(GeneratedRecipe::secondaryOutputCount)
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
        buffer.writeVarInt(recipe.outputCount());
        buffer.writeVarInt(recipe.outputCountMax());
        buffer.writeBoolean(recipe.secondaryResultId().isPresent());
        if (recipe.secondaryResultId().isPresent()) {
            Identifier.STREAM_CODEC.encode(buffer, recipe.secondaryResultId().get());
        }
        buffer.writeVarInt(recipe.secondaryOutputCount());
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
        int outputCount = buffer.readVarInt();
        int outputCountMax = buffer.readVarInt();
        Optional<Identifier> secondaryResultId = buffer.readBoolean()
                ? Optional.of(Identifier.STREAM_CODEC.decode(buffer))
                : Optional.empty();
        int secondaryOutputCount = buffer.readVarInt();
        return new GeneratedRecipe(resultId, tier, specificComponents, genericComponents, outputCount, outputCountMax, secondaryResultId, secondaryOutputCount);
    }
}
