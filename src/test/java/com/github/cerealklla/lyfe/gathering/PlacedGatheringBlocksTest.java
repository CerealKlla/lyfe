package com.github.cerealklla.lyfe.gathering;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;

class PlacedGatheringBlocksTest {

    private static final GlobalPos POS = GlobalPos.of(Level.OVERWORLD, new BlockPos(1, 64, 1));

    @Test
    void unplacedPositionReportsNotPlaced() {
        PlacedGatheringBlocks data = new PlacedGatheringBlocks();
        assertFalse(data.isPlaced(POS));
        assertTrue(data.isEmpty());
    }

    @Test
    void markThenClearRoundTrips() {
        PlacedGatheringBlocks data = new PlacedGatheringBlocks();
        data.markPlaced(POS);
        assertTrue(data.isPlaced(POS));
        assertFalse(data.isEmpty());

        data.clearPlaced(POS);
        assertFalse(data.isPlaced(POS));
        assertTrue(data.isEmpty());
    }

    @Test
    void survivesEncodeDecodeRoundTrip() {
        PlacedGatheringBlocks original = new PlacedGatheringBlocks();
        original.markPlaced(POS);

        Codec<PlacedGatheringBlocks> codec = PlacedGatheringBlocks.TYPE.codecFactory().create(null);
        DataResult<Tag> encodeResult = codec.encodeStart(NbtOps.INSTANCE, original);
        Tag encoded = encodeResult.result().orElseThrow(() -> new AssertionError("Encode failed: " + encodeResult.error()));

        DataResult<PlacedGatheringBlocks> decodeResult = codec.parse(NbtOps.INSTANCE, encoded);
        PlacedGatheringBlocks decoded = decodeResult.result().orElseThrow(() -> new AssertionError("Decode failed: " + decodeResult.error()));

        assertTrue(decoded.isPlaced(POS));
    }
}
