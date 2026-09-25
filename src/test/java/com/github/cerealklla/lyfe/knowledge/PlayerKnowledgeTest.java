package com.github.cerealklla.lyfe.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

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
    void upgradeLocationPrecisionCreatesEntryAndReturnsChanged() {
        PlayerKnowledge knowledge = new PlayerKnowledge();

        boolean changed = knowledge.upgradeLocationPrecision(PLACE, LocationPrecision.RELATIVE);

        assertTrue(changed);
        assertEquals(Optional.of(LocationPrecision.RELATIVE), knowledge.get(PLACE).flatMap(KnowledgeEntry::locationPrecision));
    }

    @Test
    void upgradeLocationPrecisionOnlyRaisesNeverLowers() {
        PlayerKnowledge knowledge = new PlayerKnowledge();
        knowledge.upgradeLocationPrecision(PLACE, LocationPrecision.EXACT);

        boolean changedByLower = knowledge.upgradeLocationPrecision(PLACE, LocationPrecision.RELATIVE);
        boolean changedBySame = knowledge.upgradeLocationPrecision(PLACE, LocationPrecision.EXACT);

        assertFalse(changedByLower);
        assertFalse(changedBySame);
        assertEquals(Optional.of(LocationPrecision.EXACT), knowledge.get(PLACE).flatMap(KnowledgeEntry::locationPrecision));
    }

    @Test
    void markVisitedForcesExactPrecisionRegardlessOfPriorSignKnowledge() {
        PlayerKnowledge knowledge = new PlayerKnowledge();
        knowledge.upgradeLocationPrecision(PLACE, LocationPrecision.RELATIVE);

        boolean changed = knowledge.markVisited(PLACE);

        assertTrue(changed);
        KnowledgeEntry entry = knowledge.get(PLACE).orElseThrow();
        assertTrue(entry.visited());
        assertEquals(Optional.of(LocationPrecision.EXACT), entry.locationPrecision());
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
        assertEquals(Optional.empty(), entry.locationPrecision());
    }

    @Test
    void survivesEncodeDecodeRoundTrip() {
        PlayerKnowledge original = new PlayerKnowledge();
        original.upgradeLocationPrecision(PLACE, LocationPrecision.APPROXIMATE);
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
