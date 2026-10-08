package com.github.cerealklla.lyfe.map.cache;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * In-memory, lazily-disk-backed cache of sampled terrain, keyed by dimension + region (32x32 chunks,
 * matching vanilla's own region-file granularity for a familiar mental model -- see {@link
 * TerrainCacheIo} for the on-disk format and {@link MapStorageKey} for where files live). A region
 * only allocates storage for chunks actually sampled (a sparse {@code Map<Integer, short[256]>}, key
 * {@code localChunkX*32+localChunkZ}), not a fully-padded 1024-chunk array, since most regions a
 * player visits are only partially explored.
 *
 * <p>Replaces live terrain sampling for {@code map.client.MapScreen} (2026-10-07, explicit user
 * request/correction -- the Map was originally built live-sampling like the minimap, which the user
 * pointed out directly contradicted their original spec: "store the bitmap... dynamically created and
 * edited by the player moving around the world"). The minimap keeps its own separate, always-live
 * {@code minimap.ClientTerrainSampler} path unchanged.
 */
public final class TerrainCache {

    private record RegionKey(ResourceKey<Level> dimension, int regionX, int regionZ) {
    }

    private static final Map<RegionKey, Region> loaded = new HashMap<>();

    private static final class Region {
        final Map<Integer, short[]> chunks;
        boolean dirty;

        Region(Map<Integer, short[]> chunks) {
            this.chunks = chunks;
        }
    }

    private TerrainCache() {
    }

    public static short get(ResourceKey<Level> dimension, int blockX, int blockZ) {
        Region region = regionFor(dimension, blockX, blockZ, false);
        if (region == null) {
            return MapPalette.UNSAMPLED;
        }
        short[] chunk = region.chunks.get(chunkKey(blockX, blockZ));
        return chunk == null ? MapPalette.UNSAMPLED : chunk[columnIndex(blockX, blockZ)];
    }

    public static void put(ResourceKey<Level> dimension, int blockX, int blockZ, short value) {
        Region region = regionFor(dimension, blockX, blockZ, true);
        short[] chunk = region.chunks.computeIfAbsent(chunkKey(blockX, blockZ), k -> {
            short[] fresh = new short[256];
            java.util.Arrays.fill(fresh, MapPalette.UNSAMPLED);
            return fresh;
        });
        chunk[columnIndex(blockX, blockZ)] = value;
        region.dirty = true;
    }

    public static boolean isChunkSampled(ResourceKey<Level> dimension, int blockX, int blockZ) {
        Region region = regionFor(dimension, blockX, blockZ, false);
        return region != null && region.chunks.containsKey(chunkKey(blockX, blockZ));
    }

    /** Writes every dirty region to disk and clears their dirty flags -- called periodically and on logout. */
    public static void flushDirty() {
        for (Map.Entry<RegionKey, Region> entry : loaded.entrySet()) {
            Region region = entry.getValue();
            if (!region.dirty) {
                continue;
            }
            Path dir = MapStorageKey.regionDirectory(entry.getKey().dimension());
            if (dir == null) {
                continue;
            }
            TerrainCacheIo.write(regionFile(dir, entry.getKey().regionX(), entry.getKey().regionZ()), region.chunks);
            region.dirty = false;
        }
    }

    /** Drops every loaded region from memory (unflushed changes are lost -- call {@link #flushDirty()} first) -- called on disconnect so a new world/server starts clean. */
    public static void clearAll() {
        loaded.clear();
    }

    private static Region regionFor(ResourceKey<Level> dimension, int blockX, int blockZ, boolean createIfAbsent) {
        int regionX = Math.floorDiv(blockX >> 4, 32);
        int regionZ = Math.floorDiv(blockZ >> 4, 32);
        RegionKey key = new RegionKey(dimension, regionX, regionZ);
        Region region = loaded.get(key);
        if (region != null) {
            return region;
        }
        Path dir = MapStorageKey.regionDirectory(dimension);
        Map<Integer, short[]> chunks = dir == null ? new HashMap<>() : TerrainCacheIo.read(regionFile(dir, regionX, regionZ));
        if (chunks.isEmpty() && !createIfAbsent) {
            // Still cache the empty result -- avoids re-reading a known-missing file every call.
            region = new Region(chunks);
            loaded.put(key, region);
            return null;
        }
        region = new Region(chunks);
        loaded.put(key, region);
        return region;
    }

    private static Path regionFile(Path dir, int regionX, int regionZ) {
        return dir.resolve("r." + regionX + "." + regionZ + ".dat");
    }

    private static int chunkKey(int blockX, int blockZ) {
        int localChunkX = Math.floorMod(blockX >> 4, 32);
        int localChunkZ = Math.floorMod(blockZ >> 4, 32);
        return localChunkX * 32 + localChunkZ;
    }

    private static int columnIndex(int blockX, int blockZ) {
        int localX = Math.floorMod(blockX, 16);
        int localZ = Math.floorMod(blockZ, 16);
        return localX * 16 + localZ;
    }
}
