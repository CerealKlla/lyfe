package com.github.cerealklla.lyfe.gathering;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Makes leaves left behind by a cut-down tree decay roughly 10x faster (user request, 2026-10-03) --
 * purely cosmetic/QoL, not a Lumberjack skill perk, so it applies unconditionally to every log break.
 *
 * <p>Confirmed against the real decompiled {@code LeavesBlock.java}: decay itself is deterministic
 * (100% once a leaf is eligible -- not persistent, and its {@code DISTANCE} property has reached
 * {@link LeavesBlock#DECAY_DISTANCE}); the only randomness is vanilla's own per-chunk random-tick
 * sampling deciding HOW OFTEN a given leaf gets checked at all (a flat {@code randomTickSpeed / 4096}
 * chance per eligible block per tick, since vanilla samples that many random positions per 16x16x16
 * sub-chunk per tick). "10x faster decay" is therefore implemented literally as 10x that same
 * per-tick chance, applied only to leaves within a bounded radius of a just-broken log for a limited
 * window afterward -- not a global change to every leaf in every loaded chunk forever, which would
 * also speed up untouched forests the player never interacted with.
 *
 * <p>Scanned every {@link #SCAN_INTERVAL_TICKS} ticks rather than every tick (cost-bounded: a cube
 * scan around every recently-cut log, several times a second, is cheap; every single tick for up to
 * 30 seconds per tree is not) -- the per-check chance is scaled up by the same interval to keep the
 * overall expected decay rate honestly "10x vanilla," not just "10x vanilla every 5th tick."
 */
public final class LeafDecayAccelerator {

    private static final int DECAY_BOOST_MULTIPLIER = 10;
    private static final int SUB_CHUNK_VOLUME = 16 * 16 * 16;
    private static final int SCAN_INTERVAL_TICKS = 5;

    // Matches LeavesBlock.DECAY_DISTANCE (7) -- the furthest a leaf can be chained from a log and
    // still be considered part of the same tree.
    private static final int SCAN_RADIUS = LeavesBlock.DECAY_DISTANCE;

    // Long enough for LeavesBlock's own DISTANCE-recalculation cascade (one ring per tick, via its
    // scheduled tick/updateShape chain) to finish propagating outward across a realistic tree canopy,
    // plus the boosted decay itself to finish.
    private static final int REGION_LIFETIME_TICKS = 20 * 30;

    private final List<ActiveRegion> activeRegions = new ArrayList<>();

    private record ActiveRegion(ServerLevel level, BlockPos center, long expiresAtTick) {
    }

    @SubscribeEvent
    public void onBlockBreak(BreakBlockEvent event) {
        if (!event.getState().is(BlockTags.LOGS)) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        activeRegions.add(new ActiveRegion(serverLevel, event.getPos().immutable(), serverLevel.getGameTime() + REGION_LIFETIME_TICKS));
    }

    @SubscribeEvent
    public void onLevelTick(LevelTickEvent.Post event) {
        if (activeRegions.isEmpty() || !(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        long now = serverLevel.getGameTime();
        activeRegions.removeIf(region -> region.level() == serverLevel && now >= region.expiresAtTick());
        if (now % SCAN_INTERVAL_TICKS != 0) {
            return;
        }

        RandomSource random = serverLevel.getRandom();
        int randomTickSpeed = serverLevel.getGameRules().get(GameRules.RANDOM_TICK_SPEED);
        double perCheckChance = DECAY_BOOST_MULTIPLIER * SCAN_INTERVAL_TICKS * (randomTickSpeed / (double) SUB_CHUNK_VOLUME);

        for (ActiveRegion region : activeRegions) {
            if (region.level() != serverLevel) {
                continue;
            }
            BlockPos.betweenClosed(
                    region.center().offset(-SCAN_RADIUS, -SCAN_RADIUS, -SCAN_RADIUS),
                    region.center().offset(SCAN_RADIUS, SCAN_RADIUS, SCAN_RADIUS)
            ).forEach(pos -> tryAccelerateDecay(serverLevel, pos, random, perCheckChance));
        }
    }

    private void tryAccelerateDecay(ServerLevel level, BlockPos pos, RandomSource random, double perCheckChance) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof LeavesBlock)) {
            return;
        }
        if (state.getValue(LeavesBlock.PERSISTENT) || state.getValue(LeavesBlock.DISTANCE) != LeavesBlock.DECAY_DISTANCE) {
            return;
        }
        if (random.nextDouble() < perCheckChance) {
            Block.dropResources(state, level, pos.immutable());
            level.removeBlock(pos, false);
        }
    }
}
