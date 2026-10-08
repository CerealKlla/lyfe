package com.github.cerealklla.lyfe.map.cache;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Binary read/write for one region file (see {@link TerrainCache}) -- a simple sequential format,
 * not a padded fixed-size layout, since a whole region is always read/written together (no in-place
 * random access needed): {@code MAGIC, version, chunkCount}, then {@code chunkCount} records of
 * {@code (localChunkX: byte, localChunkZ: byte, data: short[256])}, one only for each chunk actually
 * sampled -- an unexplored region has no file at all, and a partially-explored one only lists its
 * explored chunks, not all 1024 possible slots.
 */
final class TerrainCacheIo {

    private static final int MAGIC = 0x4C594D43; // "LYMC"
    private static final int VERSION = 1;

    private TerrainCacheIo() {
    }

    static Map<Integer, short[]> read(Path file) {
        Map<Integer, short[]> chunks = new HashMap<>();
        if (!Files.isRegularFile(file)) {
            return chunks;
        }
        try (DataInputStream in = new DataInputStream(Files.newInputStream(file))) {
            if (in.readInt() != MAGIC || in.readInt() != VERSION) {
                return chunks; // Unrecognized/corrupt file -- treat as empty rather than crash.
            }
            int chunkCount = in.readShort() & 0xFFFF;
            for (int i = 0; i < chunkCount; i++) {
                int localX = in.readUnsignedByte();
                int localZ = in.readUnsignedByte();
                short[] data = new short[256];
                for (int j = 0; j < 256; j++) {
                    data[j] = in.readShort();
                }
                chunks.put(localX * 32 + localZ, data);
            }
        } catch (IOException e) {
            return new HashMap<>(); // Corrupt/partial file -- treat as unexplored rather than crash.
        }
        return chunks;
    }

    static void write(Path file, Map<Integer, short[]> chunks) {
        try {
            Files.createDirectories(file.getParent());
            try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(file))) {
                out.writeInt(MAGIC);
                out.writeInt(VERSION);
                out.writeShort(chunks.size());
                for (Map.Entry<Integer, short[]> entry : chunks.entrySet()) {
                    int key = entry.getKey();
                    out.writeByte(key / 32);
                    out.writeByte(key % 32);
                    for (short value : entry.getValue()) {
                        out.writeShort(value);
                    }
                }
            }
        } catch (IOException e) {
            // Best-effort persistence -- a failed write just means this region's progress is lost on
            // next load, not a crash; nothing else in this mod depends on it succeeding.
        }
    }
}
