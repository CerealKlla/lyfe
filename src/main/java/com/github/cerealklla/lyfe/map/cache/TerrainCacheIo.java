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

    /**
     * Writes to a sibling temp file, then atomically renames it over {@code file} only once every
     * byte is safely out -- never opens {@code file} itself for writing. <b>Real bug fixed
     * 2026-10-10</b> (report: "client side saved Map seems to get deleted, either at end of session
     * or after server update/new build"): {@code Files.newOutputStream(file)} truncates the target
     * the instant it opens, before any new content is written -- any interruption partway through
     * (an exception, a transient file lock, the client process exiting mid-write, which lines up
     * with both reported triggers) left a previously-good region file truncated to empty rather than
     * either its old or new content. A rename is a single atomic filesystem operation, so a crash at
     * any point before it leaves the *old* file completely intact, and after it leaves the *new* one
     * completely intact -- never a half-written file either way.
     */
    static void write(Path file, Map<Integer, short[]> chunks) {
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(tmp))) {
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
            Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            // Best-effort persistence -- a failed write just means this region's progress is lost on
            // next load, not a crash; nothing else in this mod depends on it succeeding. The real
            // file itself is never touched until the move succeeds, so a failure here can only ever
            // lose this attempt's *new* data, never destroy what was already safely on disk.
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
                // Stray .tmp file left behind -- harmless, overwritten by the next successful write.
            }
        }
    }
}
