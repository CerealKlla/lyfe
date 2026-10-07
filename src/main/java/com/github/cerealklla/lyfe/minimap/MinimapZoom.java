package com.github.cerealklla.lyfe.minimap;

/**
 * Radius math for the minimap -- the player's current Expeditionist skill level sets the maximum
 * real-world radius the minimap could show (moved here from Cartographyr 2026-10-07, explicit user
 * request: "how far you can see" fits the exploration skill better than the knowledge skill).
 * Player-adjustable zoom (press-to-step in/out) was removed the same day, also explicit user
 * request, after the old fully-zoomed-out step turned out to be the one causing a real "tearing" bug
 * (sampling past the client's actual render distance -- see {@code MinimapOverlay#maybeResample}'s
 * own render-distance clamp, added the same day). Rather than defaulting to that same problematic
 * full-zoom-out view with the zoom controls just removed, this is now permanently fixed at {@link
 * #FIXED_ZOOM_MULTIPLIER} -- the old *closest* zoom step, confirmed live to never exhibit the
 * tearing even before the render-distance clamp existed.
 */
public final class MinimapZoom {

    public static final int BASE_RADIUS_BLOCKS = 64;
    public static final int PER_LEVEL_RADIUS_BLOCKS = 8;
    public static final int MAX_RADIUS_BLOCKS = 256;

    // The old ZOOM_STEPS' closest-in entry (0.25) -- see this class's own doc for why this is the
    // fixed value now, not 1.0 (the old default/fully-zoomed-out step).
    private static final double FIXED_ZOOM_MULTIPLIER = 0.25;

    private MinimapZoom() {
    }

    public static int radiusFor(int expeditionistLevel) {
        int maxRadius = Math.min(MAX_RADIUS_BLOCKS, BASE_RADIUS_BLOCKS + expeditionistLevel * PER_LEVEL_RADIUS_BLOCKS);
        return Math.max(8, (int) Math.round(maxRadius * FIXED_ZOOM_MULTIPLIER));
    }
}
