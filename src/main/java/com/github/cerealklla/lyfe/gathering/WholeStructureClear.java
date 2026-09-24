package com.github.cerealklla.lyfe.gathering;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Flood-fills outward from a broken block to find every directly (face-)connected block of the
 * exact same type (design doc Section 6: "flood-fill same-type log/ore blocks from the broken
 * block"). Needs a live level to sample blocks, so unlike the rest of Phase 2 this can't be unit
 * tested the same way -- same limitation Cartographyr's {@code NaturalRegionDiscovery} has.
 */
public final class WholeStructureClear {

    // Bounds worst-case cost per trigger. Comfortably larger than any vanilla tree or ore vein.
    private static final int MAX_BLOCKS = 64;

    private WholeStructureClear() {
    }

    /** Connected same-type blocks reachable from {@code origin}, excluding {@code origin} itself. */
    public static Set<BlockPos> connectedBlocksOf(BlockGetter level, BlockPos origin, BlockState matchState) {
        Set<BlockPos> found = new HashSet<>();
        Deque<BlockPos> frontier = new ArrayDeque<>();
        frontier.add(origin);
        found.add(origin);

        while (!frontier.isEmpty() && found.size() < MAX_BLOCKS) {
            BlockPos current = frontier.poll();
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = current.relative(direction);
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
