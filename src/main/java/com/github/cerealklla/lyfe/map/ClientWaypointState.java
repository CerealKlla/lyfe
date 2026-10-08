package com.github.cerealklla.lyfe.map;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * The single active waypoint (design spec, 2026-10-07: "only 1 waypoint active"), purely client-side
 * and ephemeral -- no server round-trip, no persistence, per the user's own explicit answer ("Storage
 * ... no separate logic" beyond the client minimap arrow). Placed/removed from {@code
 * map.client.MapScreen}'s right-click handler; auto-cleared by {@code LyfeModClient}'s client-tick
 * proximity check (~10 blocks) and on logout, and (a deliberate simplification not covered by the
 * original spec) on a dimension change, so the minimap arrow never points through a dimension
 * boundary at a position that no longer means anything relative to the player.
 */
public final class ClientWaypointState {

    private static BlockPos pos;
    private static ResourceKey<Level> dimension;

    private ClientWaypointState() {
    }

    public static void set(BlockPos newPos, ResourceKey<Level> newDimension) {
        pos = newPos;
        dimension = newDimension;
    }

    public static void clear() {
        pos = null;
        dimension = null;
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
}
