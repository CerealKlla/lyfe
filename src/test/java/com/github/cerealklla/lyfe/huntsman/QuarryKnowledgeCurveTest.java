package com.github.cerealklla.lyfe.huntsman;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.Identifier;

class QuarryKnowledgeCurveTest {

    private static final Identifier ZOMBIE = Identifier.withDefaultNamespace("zombie");
    private static final Identifier PHANTOM = Identifier.withDefaultNamespace("phantom");

    @Test
    void baseKillsForLevel1IsFiveExceptPhantom() {
        assertEquals(5, QuarryKnowledgeCurve.baseKillsForLevel1(ZOMBIE));
        assertEquals(1, QuarryKnowledgeCurve.baseKillsForLevel1(PHANTOM));
    }

    @Test
    void killsRequiredForLevelDoublesEachLevel() {
        assertEquals(5, QuarryKnowledgeCurve.killsRequiredForLevel(ZOMBIE, 1));
        assertEquals(10, QuarryKnowledgeCurve.killsRequiredForLevel(ZOMBIE, 2));
        assertEquals(20, QuarryKnowledgeCurve.killsRequiredForLevel(ZOMBIE, 3));
        assertEquals(1, QuarryKnowledgeCurve.killsRequiredForLevel(PHANTOM, 1));
        assertEquals(2, QuarryKnowledgeCurve.killsRequiredForLevel(PHANTOM, 2));
        assertEquals(4, QuarryKnowledgeCurve.killsRequiredForLevel(PHANTOM, 3));
    }

    @Test
    void cumulativeKillsForLevelSumsThePrecedingRequirements() {
        assertEquals(0, QuarryKnowledgeCurve.cumulativeKillsForLevel(ZOMBIE, 0));
        assertEquals(5, QuarryKnowledgeCurve.cumulativeKillsForLevel(ZOMBIE, 1));
        assertEquals(15, QuarryKnowledgeCurve.cumulativeKillsForLevel(ZOMBIE, 2));
        assertEquals(35, QuarryKnowledgeCurve.cumulativeKillsForLevel(ZOMBIE, 3));
    }

    @Test
    void levelForKillsReflectsCumulativeThresholds() {
        assertEquals(0, QuarryKnowledgeCurve.levelForKills(ZOMBIE, 0));
        assertEquals(0, QuarryKnowledgeCurve.levelForKills(ZOMBIE, 4));
        assertEquals(1, QuarryKnowledgeCurve.levelForKills(ZOMBIE, 5));
        assertEquals(1, QuarryKnowledgeCurve.levelForKills(ZOMBIE, 14));
        assertEquals(2, QuarryKnowledgeCurve.levelForKills(ZOMBIE, 15));
        assertEquals(2, QuarryKnowledgeCurve.levelForKills(ZOMBIE, 34));
        assertEquals(3, QuarryKnowledgeCurve.levelForKills(ZOMBIE, 35));
    }

    @Test
    void phantomLevelsMuchFaster() {
        assertEquals(1, QuarryKnowledgeCurve.levelForKills(PHANTOM, 1));
        assertEquals(1, QuarryKnowledgeCurve.levelForKills(PHANTOM, 2));
        assertEquals(2, QuarryKnowledgeCurve.levelForKills(PHANTOM, 3));
    }

    @Test
    void fractionToNextLevelResetsAfterLevelingUp() {
        assertEquals(0.0, QuarryKnowledgeCurve.fractionToNextLevel(ZOMBIE, 0));
        assertEquals(0.8, QuarryKnowledgeCurve.fractionToNextLevel(ZOMBIE, 4));
        assertEquals(0.0, QuarryKnowledgeCurve.fractionToNextLevel(ZOMBIE, 5));
        assertEquals(0.9, QuarryKnowledgeCurve.fractionToNextLevel(ZOMBIE, 14));
    }
}
