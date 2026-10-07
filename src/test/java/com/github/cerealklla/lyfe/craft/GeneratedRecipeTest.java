package com.github.cerealklla.lyfe.craft;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.mojang.serialization.DataResult;

import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;

class GeneratedRecipeTest {

    @Test
    void survivesEncodeDecodeRoundTrip() {
        GeneratedRecipe original = new GeneratedRecipe(
                Identifier.withDefaultNamespace("stone_sword"),
                1,
                Map.of(Identifier.withDefaultNamespace("cobblestone"), 2, Identifier.withDefaultNamespace("stick"), 1),
                Map.of("Any Log", 1));

        DataResult<Tag> encoded = GeneratedRecipe.CODEC.encodeStart(NbtOps.INSTANCE, original);
        Tag tag = encoded.getOrThrow();
        GeneratedRecipe decoded = GeneratedRecipe.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();

        assertEquals(original.resultId(), decoded.resultId());
        assertEquals(original.tier(), decoded.tier());
        assertEquals(original.specificComponents(), decoded.specificComponents());
        assertEquals(original.genericComponents(), decoded.genericComponents());
    }

    @Test
    void slotsUsedCountsDistinctComponentsAcrossBothMaps() {
        GeneratedRecipe recipe = new GeneratedRecipe(
                Identifier.withDefaultNamespace("iron_axe"),
                2,
                Map.of(Identifier.withDefaultNamespace("iron_ingot"), 3),
                Map.of("Any Log", 1));
        assertEquals(2, recipe.slotsUsed());
    }
}
