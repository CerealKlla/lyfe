package com.github.cerealklla.lyfe.map;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * The server-pushed "quest hint" waypoint -- a {@link QuestWaypointPayload} overwrites this
 * whenever a mod (e.g. Myssions, via {@code api.Lyfe#setQuestWaypoint}) wants to show the local
 * player a vague target area. Deliberately a separate state from {@link ClientWaypointState} (the
 * player's own manually-placed pin) -- the two can coexist, have different owners (this one is
 * server-authoritative, that one is purely client-local), and render with different visuals
 * (this one draws as a circle on {@code client.MapScreen}, not just a dot).
 */
public final class ClientQuestWaypointState {

    private static BlockPos pos;
    private static ResourceKey<Level> dimension;
    private static double radiusBlocks;

    private ClientQuestWaypointState() {
    }

    public static void set(BlockPos newPos, ResourceKey<Level> newDimension, double newRadiusBlocks) {
        pos = newPos;
        dimension = newDimension;
        radiusBlocks = newRadiusBlocks;
    }

    public static void clear() {
        pos = null;
        dimension = null;
        radiusBlocks = 0;
    }

    public static boolean isSet() {
        return pos != null;
    }

    public static Optional<BlockPos> pos() {
        return Optional.ofNullable(pos);
    }

    public static Optional<ResourceKey<Level>> dimension() {
        return Optional.ofNullable(dimension);
    }

    public static double radiusBlocks() {
        return radiusBlocks;
    }
}
