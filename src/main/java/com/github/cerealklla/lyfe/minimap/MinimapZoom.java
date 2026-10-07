package com.github.cerealklla.lyfe.minimap;

/**
 * Radius/zoom math for the minimap -- the player's current Cartographyr skill level sets the
 * maximum real-world radius the minimap can show (ties the minimap's usefulness to the same skill
 * Lyfe already uses for Cartographyr sign/map knowledge); the player can additionally zoom in below
 * that maximum via {@link #ZOOM_STEPS}, a small discrete multiplier list rather than continuous
 * scroll-to-zoom -- simpler input handling (two keybinds, see {@code LyfeModClient}), and matches
 * this pass's "Fixed mode only, Auto-Rotate deferred" scope in spirit (no continuous input state to
 * juggle alongside a future rotation mode).
 */
public final class MinimapZoom {

    public static final int BASE_RADIUS_BLOCKS = 64;
    public static final int PER_LEVEL_RADIUS_BLOCKS = 8;
    public static final int MAX_RADIUS_BLOCKS = 256;

    /** Multiplied against the level-gated max radius; index 0 is the closest zoom, the last entry (1.0) is fully zoomed out (the default). */
    public static final double[] ZOOM_STEPS = {0.25, 0.5, 0.75, 1.0};
    public static final int DEFAULT_STEP = ZOOM_STEPS.length - 1;

    private MinimapZoom() {
    }

    public static int maxRadiusForLevel(int cartographyrLevel) {
        return Math.min(MAX_RADIUS_BLOCKS, BASE_RADIUS_BLOCKS + cartographyrLevel * PER_LEVEL_RADIUS_BLOCKS);
    }

    public static int radiusFor(int cartographyrLevel, int zoomStep) {
        int maxRadius = maxRadiusForLevel(cartographyrLevel);
        return Math.max(8, (int) Math.round(maxRadius * ZOOM_STEPS[zoomStep]));
    }

    public static int clampStep(int step) {
        return Math.max(0, Math.min(ZOOM_STEPS.length - 1, step));
    }
}
