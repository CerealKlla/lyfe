package com.github.cerealklla.lyfe.craft;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.Identifier;

class RecipeGeneratorTest {

    @Test
    void generatedRecipeUsesBetween50And100PercentOfTierCapacity() {
        Random random = new Random(42);
        for (int tier = EquipmentTierLadder.MIN_TIER; tier <= EquipmentTierLadder.MAX_TIER; tier++) {
            int capacity = EquipmentTierLadder.slotCapacity(tier);
            // Tier 0 (Wood) bypasses the 50%-100% formula entirely -- a flat uniform 1..capacity
            // roll instead (2026-10-03, user request), so its own floor is just 1.
            int minSlots = tier == 0 ? 1 : (int) Math.ceil(capacity * 0.5);
            Identifier resultId = EquipmentTierLadder.itemId(EquipmentTierLadder.ToolType.SWORD, tier);
            Identifier previousTierItem = tier > 0 ? EquipmentTierLadder.itemId(EquipmentTierLadder.ToolType.SWORD, tier - 1) : null;
            GeneratedRecipe recipe = RecipeGenerator.generate(resultId, tier, previousTierItem, random);
            assertTrue(recipe.slotsUsed() >= minSlots, "tier " + tier + " used fewer than its minimum");
            assertTrue(recipe.slotsUsed() <= capacity, "tier " + tier + " used more than its own capacity");
        }
    }

    @Test
    void generatedRecipeNeverHasEmptyComponents() {
        Random random = new Random(7);
        GeneratedRecipe recipe = RecipeGenerator.generate(
                EquipmentTierLadder.itemId(EquipmentTierLadder.ToolType.PICKAXE, 1), 1,
                EquipmentTierLadder.itemId(EquipmentTierLadder.ToolType.PICKAXE, 0), random);
        assertFalse(recipe.specificComponents().isEmpty() && recipe.genericComponents().isEmpty());
        recipe.specificComponents().values().forEach(qty -> assertTrue(qty >= 1));
        recipe.genericComponents().values().forEach(qty -> assertTrue(qty >= 1));
    }

    @Test
    void tier1PlusRequiresExactlyOnePreviousTierItem() {
        Random random = new Random(99);
        Identifier previousTierItem = EquipmentTierLadder.itemId(EquipmentTierLadder.ToolType.AXE, 2);
        GeneratedRecipe recipe = RecipeGenerator.generate(
                EquipmentTierLadder.itemId(EquipmentTierLadder.ToolType.AXE, 3), 3, previousTierItem, random);
        assertTrue(recipe.specificComponents().containsKey(previousTierItem));
        assertTrue(recipe.specificComponents().get(previousTierItem) == 1);
    }
}
