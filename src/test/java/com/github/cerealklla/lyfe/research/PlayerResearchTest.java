package com.github.cerealklla.lyfe.research;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.mojang.serialization.DataResult;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;

class PlayerResearchTest {

    private static final Identifier STONE_SWORD = Identifier.withDefaultNamespace("stone_sword");

    @Test
    void startsWithNothingLearned() {
        PlayerResearch research = new PlayerResearch();
        assertFalse(research.isLearned(STONE_SWORD));
        assertEquals(0, research.researchPoints(STONE_SWORD));
    }

    @Test
    void accumulatesPointsUntilThresholdThenLearns() {
        PlayerResearch research = new PlayerResearch();
        research.addResearchPoints(STONE_SWORD, 10, 25);
        assertFalse(research.isLearned(STONE_SWORD));
        assertEquals(10, research.researchPoints(STONE_SWORD));

        research.addResearchPoints(STONE_SWORD, 20, 25);
        assertTrue(research.isLearned(STONE_SWORD));
    }

    @Test
    void reResearchingAnAlreadyLearnedItemIsANoOp() {
        PlayerResearch research = new PlayerResearch();
        research.addResearchPoints(STONE_SWORD, 25, 25);
        assertTrue(research.isLearned(STONE_SWORD));

        boolean changed = research.addResearchPoints(STONE_SWORD, 50, 25);
        assertFalse(changed);
        assertEquals(0, research.researchPoints(STONE_SWORD));
    }

    @Test
    void survivesEncodeDecodeRoundTrip() {
        PlayerResearch research = new PlayerResearch();
        research.addResearchPoints(STONE_SWORD, 10, 25);

        DataResult<CompoundTag> encoded = PlayerResearch.CODEC.codec().encodeStart(NbtOps.INSTANCE, research)
                .map(tag -> (CompoundTag) tag);
        CompoundTag tag = encoded.getOrThrow();
        PlayerResearch decoded = PlayerResearch.CODEC.codec().parse(NbtOps.INSTANCE, tag).getOrThrow();

        assertEquals(10, decoded.researchPoints(STONE_SWORD));
        assertFalse(decoded.isLearned(STONE_SWORD));
    }
}
