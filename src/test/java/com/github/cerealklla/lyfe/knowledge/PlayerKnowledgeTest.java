package com.github.cerealklla.lyfe.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.mojang.serialization.DataResult;

import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

class PlayerKnowledgeTest {

    private static final long PLACE = 1L;

    @Test
    void unknownEntityHasNoEntry() {
        PlayerKnowledge knowledge = new PlayerKnowledge();
        assertEquals(Optional.empty(), knowledge.get(PLACE));
    }

    @Test
    void learnLocationFactorsCreatesEntryAndReturnsChanged() {
        PlayerKnowledge knowledge = new PlayerKnowledge();

        boolean changed = knowledge.learnLocationFactors(PLACE, Set.of(KnowledgeFactor.DIRECTION));

        assertTrue(changed);
        assertEquals(Set.of(KnowledgeFactor.DIRECTION), knowledge.get(PLACE).orElseThrow().locationFactors());
    }

    @Test
    void learnLocationFactorsIsPurelyAdditiveAndOnlyChangedWhenSomethingNewIsGained() {
        PlayerKnowledge knowledge = new PlayerKnowledge();
        knowledge.learnLocationFactors(PLACE, EnumSet.allOf(KnowledgeFactor.class));

        boolean changedByFewer = knowledge.learnLocationFactors(PLACE, Set.of(KnowledgeFactor.DIRECTION));
        boolean changedBySame = knowledge.learnLocationFactors(PLACE, EnumSet.allOf(KnowledgeFactor.class));

        assertFalse(changedByFewer);
        assertFalse(changedBySame);
        assertEquals(EnumSet.allOf(KnowledgeFactor.class), knowledge.get(PLACE).orElseThrow().locationFactors());
    }

    @Test
    void learnLocationFactorsUnionsAcrossMultipleLearningEvents() {
        PlayerKnowledge knowledge = new PlayerKnowledge();
        knowledge.learnLocationFactors(PLACE, Set.of(KnowledgeFactor.DIRECTION));

        boolean changed = knowledge.learnLocationFactors(PLACE, Set.of(KnowledgeFactor.DISTANCE));

        assertTrue(changed);
        assertEquals(Set.of(KnowledgeFactor.DIRECTION, KnowledgeFactor.DISTANCE), knowledge.get(PLACE).orElseThrow().locationFactors());
    }

    @Test
    void markVisitedForcesAllFactorsRegardlessOfPriorSignKnowledge() {
        PlayerKnowledge knowledge = new PlayerKnowledge();
        knowledge.learnLocationFactors(PLACE, Set.of(KnowledgeFactor.DIRECTION));

        boolean changed = knowledge.markVisited(PLACE);

        assertTrue(changed);
        KnowledgeEntry entry = knowledge.get(PLACE).orElseThrow();
        assertTrue(entry.visited());
        assertEquals(EnumSet.allOf(KnowledgeFactor.class), entry.locationFactors());
    }

    @Test
    void markVisitedTwiceIsNotChangedTheSecondTime() {
        PlayerKnowledge knowledge = new PlayerKnowledge();
        knowledge.markVisited(PLACE);

        boolean changedAgain = knowledge.markVisited(PLACE);

        assertFalse(changedAgain);
    }

    @Test
    void markNamedAndHistoricallyKnownAreIndependentAndIdempotent() {
        PlayerKnowledge knowledge = new PlayerKnowledge();

        assertTrue(knowledge.markNamed(PLACE));
        assertFalse(knowledge.markNamed(PLACE));
        assertTrue(knowledge.markHistoricallyKnown(PLACE));
        assertFalse(knowledge.markHistoricallyKnown(PLACE));

        KnowledgeEntry entry = knowledge.get(PLACE).orElseThrow();
        assertTrue(entry.named());
        assertTrue(entry.historicallyKnown());
        assertEquals(Set.of(), entry.locationFactors());
    }

    @Test
    void survivesEncodeDecodeRoundTrip() {
        PlayerKnowledge original = new PlayerKnowledge();
        original.learnLocationFactors(PLACE, Set.of(KnowledgeFactor.DIRECTION, KnowledgeFactor.DISTANCE));
        original.markNamed(PLACE);
        original.markVisited(2L);

        var codec = PlayerKnowledge.CODEC.codec();
        DataResult<Tag> encodeResult = codec.encodeStart(NbtOps.INSTANCE, original);
        Tag encoded = encodeResult.result().orElseThrow(() -> new AssertionError("Encode failed: " + encodeResult.error()));

        DataResult<PlayerKnowledge> decodeResult = codec.parse(NbtOps.INSTANCE, encoded);
        PlayerKnowledge decoded = decodeResult.result().orElseThrow(() -> new AssertionError("Decode failed: " + decodeResult.error()));

        assertEquals(original.get(PLACE), decoded.get(PLACE));
        assertEquals(original.get(2L), decoded.get(2L));
    }
}
