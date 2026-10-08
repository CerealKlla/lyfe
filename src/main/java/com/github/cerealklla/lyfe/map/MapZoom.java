package com.github.cerealklla.lyfe.map;

/**
 * Zoom steps for the full-screen Map ({@code map.client.MapScreen}) -- separate from {@code
 * minimap.MinimapZoom}, which no longer has any player-adjustable zoom at all (see its own doc).
 * Locked to {@link #NO_ZOOM_INDEX} (1x) unless Expeditionist level >= {@code
 * ExpeditionistConstants#MAP_ZOOM_UNLOCK_LEVEL}, per the original spec ("at 25 you gain the ability
 * to zoom in or out a few clicks on the Map").
 */
public final class MapZoom {

    public static final int BASE_RADIUS_BLOCKS = 300;

    /** Index 1 ({@code 1.0}) is "no zoom" -- the only index reachable below the unlock level. */
    private static final double[] STEPS = {2.0, 1.0, 0.5, 0.25};
    public static final int NO_ZOOM_INDEX = 1;

    private MapZoom() {
    }

    public static int clampIndex(int index, boolean zoomUnlocked) {
        if (!zoomUnlocked) {
            return NO_ZOOM_INDEX;
        }
        return Math.max(0, Math.min(STEPS.length - 1, index));
    }

    public static int radiusForIndex(int index) {
        return (int) Math.round(BASE_RADIUS_BLOCKS * STEPS[index]);
    }
}
