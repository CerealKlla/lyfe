package com.github.cerealklla.lyfe.craft;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.Identifier;

class EquipmentTierLadderTest {

    @Test
    void tier0IsWoodenAndCheap() {
        assertEquals(Identifier.withDefaultNamespace("wooden_sword"),
                EquipmentTierLadder.itemId(EquipmentTierLadder.ToolType.SWORD, 0));
        assertEquals(Identifier.withDefaultNamespace("wooden_hoe"),
                EquipmentTierLadder.itemId(EquipmentTierLadder.ToolType.HOE, 0));
        assertEquals(3, EquipmentTierLadder.slotCapacity(0));
    }

    @Test
    void tier1StillMapsToStone() {
        assertEquals(Identifier.withDefaultNamespace("stone_pickaxe"),
                EquipmentTierLadder.itemId(EquipmentTierLadder.ToolType.PICKAXE, 1));
    }

    @Test
    void tier2IsCopperInsertedBetweenStoneAndIron() {
        assertEquals(Identifier.withDefaultNamespace("copper_sword"),
                EquipmentTierLadder.itemId(EquipmentTierLadder.ToolType.SWORD, 2));
        assertEquals(Identifier.withDefaultNamespace("iron_sword"),
                EquipmentTierLadder.itemId(EquipmentTierLadder.ToolType.SWORD, 3));
    }

    @Test
    void tier6IsTheSoftLegendaryNetheriteTier() {
        assertEquals(Identifier.withDefaultNamespace("netherite_sword"),
                EquipmentTierLadder.itemId(EquipmentTierLadder.ToolType.SWORD, 6));
        assertEquals(6, EquipmentTierLadder.MAX_TIER);
        assertEquals(5, EquipmentTierLadder.MAX_REACHABLE_TIER);
    }

    @Test
    void allGeneratedItemIdsIncludesWoodAndLegendary() {
        assertEquals(42, EquipmentTierLadder.allGeneratedItemIds().size());
    }

    @Test
    void spearIsARealGeneratedTool() {
        assertEquals(Identifier.withDefaultNamespace("iron_spear"),
                EquipmentTierLadder.itemId(EquipmentTierLadder.ToolType.SPEAR, 3));
    }
}
