package com.github.cerealklla.lyfe.map.cache;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.SectionPos;
import com.github.cerealklla.lyfe.minimap.ClientTerrainSampler;

/**
 * Fills {@link TerrainCache} passively as the player walks around -- "dynamically created and edited
 * by the player moving around the world," the user's own original spec, rather than only sampling
 * while the Map screen happens to be open. Hooked into {@code LyfeModClient}'s client-tick handler,
 * throttled to roughly once a second; each firing samples the player's current chunk plus its
 * immediate 1-chunk ring (so walking along a chunk border fills in both sides, not just whichever
 * chunk the player's feet happen to be in) for any chunk not already in the cache.
 *
 * <p>Runs synchronously on the client thread, not a background executor -- deliberate, so {@link
 * TerrainCache} never needs to be thread-safe (both this writer and {@code map.client.MapScreen}'s
 * reader always run on the same thread). A single chunk's 256-column sample is cheap relative to the
 * minimap's own much larger live samples, and the client already touches this same block/heightmap
 * data every frame for chunk mesh building -- any per-tick cost here is a small, bounded addition to
 * that existing load, not a new category of work.
 */
public final class TerrainCachePassiveSampler {

    private static final int INTERVAL_TICKS = 20;

    private static int ticksSinceLastCheck = INTERVAL_TICKS;

    private TerrainCachePassiveSampler() {
    }

    public static void tick(ClientLevel level, LocalPlayer player) {
        ticksSinceLastCheck++;
        if (ticksSinceLastCheck < INTERVAL_TICKS) {
            return;
        }
        ticksSinceLastCheck = 0;

        int centerChunkX = SectionPos.blockToSectionCoord(player.getBlockX());
        int centerChunkZ = SectionPos.blockToSectionCoord(player.getBlockZ());
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                sampleChunkIfNeeded(level, centerChunkX + dx, centerChunkZ + dz);
            }
        }
    }

    private static void sampleChunkIfNeeded(ClientLevel level, int chunkX, int chunkZ) {
        int originX = chunkX * 16;
        int originZ = chunkZ * 16;
        if (TerrainCache.isChunkSampled(level.dimension(), originX, originZ)) {
            return;
        }
        if (level.getChunk(chunkX, chunkZ).isEmpty()) {
            return; // Not actually loaded client-side yet -- try again on a later tick.
        }
        // radius=8/cols=16 makes ClientTerrainSampler.sampleGrid's own scaleBlocks come out to
        // exactly 1 -- one stored value per real block, matching the cache's fixed column grain.
        ClientTerrainSampler.sampleGrid(level, originX + 8, originZ + 8, 8, 8, 16, 16, true,
                (col, row, color, brightness) -> TerrainCache.put(level.dimension(), originX + col, originZ + row,
                        MapPalette.encode(color, brightness)));
    }
}
