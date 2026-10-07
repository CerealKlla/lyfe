package com.github.cerealklla.lyfe.reincarnation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class ReincarnationSlotsTest {

    @Test
    void levelZeroProtectsNothing() {
        assertTrue(ReincarnationSlots.protectedSlotsForLevel(0).isEmpty());
    }

    @Test
    void matchesTheUserSpecOrderThroughLevelEight() {
        List<ProtectedSlot> atEight = ReincarnationSlots.protectedSlotsForLevel(8);
        assertEquals(List.of(
                ProtectedSlot.HOTBAR_0, ProtectedSlot.ARMOR_CHEST, ProtectedSlot.HOTBAR_1, ProtectedSlot.ARMOR_LEGS,
                ProtectedSlot.HOTBAR_2, ProtectedSlot.ARMOR_HEAD, ProtectedSlot.HOTBAR_3, ProtectedSlot.ARMOR_FEET
        ), atEight);
    }

    @Test
    void offhandUnlocksAtLevelNine() {
        List<ProtectedSlot> atNine = ReincarnationSlots.protectedSlotsForLevel(9);
        assertEquals(ProtectedSlot.OFFHAND, atNine.get(8));
    }

    @Test
    void remainingHotbarUnlocksLevelsTenThroughFourteen() {
        List<ProtectedSlot> atMax = ReincarnationSlots.protectedSlotsForLevel(ReincarnationSlots.MAX_LEVEL);
        assertEquals(List.of(
                ProtectedSlot.HOTBAR_4, ProtectedSlot.HOTBAR_5, ProtectedSlot.HOTBAR_6,
                ProtectedSlot.HOTBAR_7, ProtectedSlot.HOTBAR_8
        ), atMax.subList(9, 14));
    }

    @Test
    void isCumulativeNotJustTheNewestSlot() {
        List<ProtectedSlot> atThree = ReincarnationSlots.protectedSlotsForLevel(3);
        assertEquals(3, atThree.size());
        assertTrue(atThree.contains(ProtectedSlot.HOTBAR_0));
        assertTrue(atThree.contains(ProtectedSlot.ARMOR_CHEST));
    }

    @Test
    void clampsAboveMaxLevel() {
        assertEquals(ReincarnationSlots.MAX_LEVEL, ReincarnationSlots.protectedSlotsForLevel(999).size());
    }

    @Test
    void armorAndOffhandAreNeverTypeFiltered() {
        assertFalse(ProtectedSlot.ARMOR_CHEST.isTypeFiltered());
        assertFalse(ProtectedSlot.ARMOR_LEGS.isTypeFiltered());
        assertFalse(ProtectedSlot.ARMOR_HEAD.isTypeFiltered());
        assertFalse(ProtectedSlot.ARMOR_FEET.isTypeFiltered());
        assertFalse(ProtectedSlot.OFFHAND.isTypeFiltered());
    }

    @Test
    void hotbarSlotsAreTypeFiltered() {
        assertTrue(ProtectedSlot.HOTBAR_0.isTypeFiltered());
        assertTrue(ProtectedSlot.HOTBAR_8.isTypeFiltered());
    }

    @Test
    void benefitLinesOnlyShowsUnlockedSlotsPlusTheNextOne() {
        List<String> lines = ReincarnationSlots.benefitLines(3);
        // 3 unlocked ("Protected: ...") + 1 "Next unlock" line.
        assertEquals(4, lines.size());
        assertTrue(lines.get(0).startsWith("Protected:"));
        assertTrue(lines.get(2).startsWith("Protected:"));
        assertTrue(lines.get(3).startsWith("Next unlock (Level 4):"));
    }

    @Test
    void benefitLinesAtLevelZeroShowsOnlyTheNextUnlock() {
        List<String> lines = ReincarnationSlots.benefitLines(0);
        assertEquals(2, lines.size());
        assertEquals("Nothing protected yet", lines.get(0));
        assertTrue(lines.get(1).startsWith("Next unlock (Level 1):"));
    }

    @Test
    void benefitLinesAtMaxLevelHasNoNextUnlockLine() {
        List<String> lines = ReincarnationSlots.benefitLines(ReincarnationSlots.MAX_LEVEL);
        assertEquals(ReincarnationSlots.MAX_LEVEL, lines.size());
        assertTrue(lines.stream().allMatch(line -> line.startsWith("Protected:")));
    }
}
