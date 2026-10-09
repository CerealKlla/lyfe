package com.github.cerealklla.lyfe.map.cache;

import java.nio.file.Path;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Resolves where the persisted terrain cache lives on disk for the player's current world --
 * {@code <gameDir>/lyfe/mapdata/<worldKey>/<dimensionPath>/}. {@code worldKey} is the singleplayer
 * save's own world seed when playing locally, else a sanitized server address.
 *
 * <p><b>Not keyed by save folder name</b> (a real bug, found 2026-10-08 during natural-settlement
 * live testing: a superflat test world showed terrain cached from a previous, unrelated test world)
 * -- this suite's dev workflow (and plenty of normal play) routinely deletes and recreates a save
 * under the same generic name ("New World"), which a folder-name key can't tell apart from the save
 * actually still being the same world.
 *
 * <p><b>Also not keyed by a random id file written inside the save directory</b> -- tried first,
 * also found broken by live testing the same day: Minecraft's own "delete this save to make room for
 * a fresh one" routine doesn't necessarily do a raw recursive wipe of the whole directory, just its
 * own known save structure (region files, playerdata, level.dat, etc.), so a marker file we wrote
 * ourselves could survive across what looked from the outside like a full deletion -- several test
 * sessions in a row kept reading back the *same* marker file and so shared the *same* cache bucket
 * again, exactly the bug this was meant to fix. The world's own seed doesn't have this problem: it's
 * generated fresh (or explicitly set) at world-creation time regardless of what any previous save at
 * that path left behind, and is naturally identical across sessions of a genuinely continuing save.
 * Multiplayer has no local filesystem access to the remote save (and no seed to read client-side
 * either), so a sanitized server address remains the best available key there -- this mod suite
 * already treats "Dev Server" and "Production" as genuinely separate addresses (see the root
 * project's own CLAUDE.md), so this still keeps their map data separate.
 */
public final class MapStorageKey {

    private MapStorageKey() {
    }

    /** Null if no world is currently joined (e.g. at the main menu) -- callers should no-op in that case. */
    public static Path regionDirectory(ResourceKey<Level> dimension) {
        Minecraft minecraft = Minecraft.getInstance();
        String worldKey;
        if (minecraft.hasSingleplayerServer() && minecraft.getSingleplayerServer() != null) {
            net.minecraft.server.level.ServerLevel overworld = minecraft.getSingleplayerServer().getLevel(Level.OVERWORLD);
            if (overworld == null) {
                return null;
            }
            worldKey = "seed_" + overworld.getSeed();
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
