package com.github.cerealklla.lyfe.gathering;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Flood-fills outward from a broken block to find every connected block of the exact same type
 * (design doc Section 6: "flood-fill same-type log/ore blocks from the broken block"). Needs a
 * live level to sample blocks, so unlike the rest of Phase 2 this can't be unit tested the same
 * way -- same limitation Cartographyr's {@code NaturalRegionDiscovery} has.
 */
public final class WholeStructureClear {

    // Bounds worst-case cost per trigger. Comfortably larger than any vanilla tree or ore vein.
    private static final int MAX_BLOCKS = 64;

    // Every offset in the 3x3x3 cube around a block except the center -- 26 neighbors, not just
    // the 6 face-adjacent ones. Fixed 2026-09-25 (see decisions.md): face-only adjacency missed a
    // tree's branch logs, since vanilla oak/large-oak generation offsets branch logs diagonally
    // from the trunk (both horizontally and vertically at once), so a branch log only ever touches
    // the trunk edge-to-edge or corner-to-corner, never face-to-face. Ore veins happen to be
    // compact enough that this rarely mattered for Miner, but it's the same underlying bug there.
    private static final List<BlockPos> NEIGHBOR_OFFSETS = buildNeighborOffsets();

    private WholeStructureClear() {
    }

    private static List<BlockPos> buildNeighborOffsets() {
        List<BlockPos> offsets = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx != 0 || dy != 0 || dz != 0) {
                        offsets.add(new BlockPos(dx, dy, dz));
                    }
                }
            }
        }
        return offsets;
    }

    /** Connected same-type blocks reachable from {@code origin}, excluding {@code origin} itself. */
    public static Set<BlockPos> connectedBlocksOf(BlockGetter level, BlockPos origin, BlockState matchState) {
        Set<BlockPos> found = new HashSet<>();
        Deque<BlockPos> frontier = new ArrayDeque<>();
        frontier.add(origin);
        found.add(origin);

        while (!frontier.isEmpty() && found.size() < MAX_BLOCKS) {
            BlockPos current = frontier.poll();
            for (BlockPos offset : NEIGHBOR_OFFSETS) {
                BlockPos neighbor = current.offset(offset);
                if (found.contains(neighbor)) {
                    continue;
                }
                if (level.getBlockState(neighbor).is(matchState.getBlock())) {
                    found.add(neighbor);
                    frontier.add(neighbor);
                    if (found.size() >= MAX_BLOCKS) {
                        break;
                    }
                }
            }
        }

        found.remove(origin);
        return found;
    }
}
