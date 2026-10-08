package com.github.cerealklla.lyfe.map.cache;

import java.nio.file.Path;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Resolves where the persisted terrain cache lives on disk for the player's current world --
 * {@code <gameDir>/lyfe/mapdata/<worldKey>/<dimensionPath>/}. {@code worldKey} is the singleplayer
 * save's own level-id when playing locally, else a sanitized server address -- this mod suite already
 * treats "Dev Server" and "Production" as genuinely separate worlds (see the root project's own
 * CLAUDE.md), so keying by address keeps their map data separate automatically, the same way a
 * singleplayer save's own folder name already would.
 */
public final class MapStorageKey {

    private MapStorageKey() {
    }

    /** Null if no world is currently joined (e.g. at the main menu) -- callers should no-op in that case. */
    public static Path regionDirectory(ResourceKey<Level> dimension) {
        Minecraft minecraft = Minecraft.getInstance();
        String worldKey;
        if (minecraft.hasSingleplayerServer() && minecraft.getSingleplayerServer() != null) {
            // storageSource.getLevelId() isn't reachable (protected) -- the server's own directory
            // name is an equally stable per-save identifier and is a public accessor.
            worldKey = sanitize(minecraft.getSingleplayerServer().getServerDirectory().getFileName().toString());
        } else if (minecraft.getCurrentServer() != null) {
            worldKey = sanitize(minecraft.getCurrentServer().ip);
        } else {
            return null;
        }
        String dimensionPath = sanitize(dimension.identifier().toString());
        return minecraft.gameDirectory.toPath().resolve("lyfe").resolve("mapdata").resolve(worldKey).resolve(dimensionPath);
    }

    private static String sanitize(String raw) {
        return raw.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
