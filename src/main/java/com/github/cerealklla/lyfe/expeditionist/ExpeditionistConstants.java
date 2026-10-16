package com.github.cerealklla.lyfe.expeditionist;

import java.util.List;

import com.github.cerealklla.lyfe.minimap.MinimapZoom;

/**
 * Tunable numbers for the Expeditionist skill (2026-10-07 user request) -- placeholder values like
 * every other first-pass constant in this mod, easy to retune. {@code location.LocationTracker}
 * grants {@link #DISCOVERY_XP} for a Region/Settlement's first-ever personal visit (never on a
 * revisit -- explicit user spec), plus {@link #GENUINE_DISCOVERY_BONUS_XP} on top specifically when
 * that visit was also what caused Cartographyr to create the Region in the first place (a real
 * {@code Cartography#discoverNaturalRegion} call returning present, not just the player's own first
 * encounter with an already-existing one) -- user's own distinction, "bonus xp for genuine
 * discoveries." Settlements can never trigger the bonus: they're always pre-founded via Settlemynts,
 * never created as a side effect of a player simply walking somewhere.
 *
 * <p>{@code minimap.MinimapOverlay} reads the first three: the minimap itself is invisible below
 * {@link #MINIMAP_UNLOCK_LEVEL}, and below {@link #ORIENTATION_TOGGLE_UNLOCK_LEVEL} its orientation
 * is locked Fixed (north-up) regardless of the player's own stored toggle state -- the {@code
 * key.lyfe.minimap_toggle_rotation} keybind itself is also a no-op below that level, so there's
 * nothing to toggle back from. {@link #ORIENTATION_TOGGLE_UNLOCK_LEVEL} doubles as the North
 * indicator's own unlock level (both arrived in the same re-shuffle, 2026-10-15, explicit user
 * request). {@code map.client.MapScreen} reads the remaining four: the full-screen Map ("M") does
 * nothing at all below {@link #MAP_UNLOCK_LEVEL}; once open, right-click placing/removing/showing
 * the single custom waypoint is locked below {@link #CUSTOM_WAYPOINT_UNLOCK_LEVEL} (the quest-origin
 * waypoint is a separate, never-level-gated concept -- see {@code map.ClientQuestWaypointState}'s own
 * doc -- and always shows on both the minimap and the Map once either is itself unlocked); settlement
 * dots/tooltips stay hidden below {@link #SETTLEMENT_DOTS_UNLOCK_LEVEL}; scroll-wheel zoom is locked
 * to one fixed step below {@link #MAP_ZOOM_UNLOCK_LEVEL}. Click-drag panning the Map is never level
 * gated at all, at any level the Map itself is open.
 *
 * <p><b>Full re-shuffle, 2026-10-15, explicit user spec</b> (previously minimap/North/Map/dots/zoom
 * sat at 5/10/15/20/25) -- reflects a deliberate "ease the player in" re-pacing, not a balance-only
 * tweak: level 1 (minimap, locked orientation) / 5 (orientation toggle, defaulting to Auto-Rotate
 * the moment it unlocks since that's already {@code ClientMinimapState}'s own stored default, plus
 * North indicator) / 10 (full Map, no custom waypoints yet) / 15 (custom waypoints) / 20 (settlement
 * dots) / 25 (Map zoom, unchanged).
 */
public final class ExpeditionistConstants {

    public static final int DISCOVERY_XP = 50;
    public static final int GENUINE_DISCOVERY_BONUS_XP = 50;

    public static final int MINIMAP_UNLOCK_LEVEL = 1;
    public static final int ORIENTATION_TOGGLE_UNLOCK_LEVEL = 5;
    public static final int NORTH_INDICATOR_UNLOCK_LEVEL = 5;
    public static final int MAP_UNLOCK_LEVEL = 10;
    public static final int CUSTOM_WAYPOINT_UNLOCK_LEVEL = 15;
    public static final int SETTLEMENT_DOTS_UNLOCK_LEVEL = 20;
    public static final int MAP_ZOOM_UNLOCK_LEVEL = 25;

    private ExpeditionistConstants() {
    }

    /** Current, already-computed values for the Skills screen -- see {@code skill.SkillBenefits}. */
    public static List<String> benefitLines(int level) {
        String minimapLine = "Minimap: " + (level >= MINIMAP_UNLOCK_LEVEL ? "unlocked (locked orientation)" : "locked (requires level " + MINIMAP_UNLOCK_LEVEL + ")");
        String orientationLine = "Minimap orientation toggle + North indicator: " + (level >= ORIENTATION_TOGGLE_UNLOCK_LEVEL ? "unlocked" : "locked (requires level " + ORIENTATION_TOGGLE_UNLOCK_LEVEL + ")");
        String mapLine = "Map (press M): " + (level >= MAP_UNLOCK_LEVEL ? "unlocked" : "locked (requires level " + MAP_UNLOCK_LEVEL + ")");
        String waypointLine = "Custom waypoints on Map: " + (level >= CUSTOM_WAYPOINT_UNLOCK_LEVEL ? "unlocked" : "locked (requires level " + CUSTOM_WAYPOINT_UNLOCK_LEVEL + ")");
        String dotsLine = "Settlement dots on Map: " + (level >= SETTLEMENT_DOTS_UNLOCK_LEVEL ? "unlocked" : "locked (requires level " + SETTLEMENT_DOTS_UNLOCK_LEVEL + ")");
        String zoomLine = "Map zoom: " + (level >= MAP_ZOOM_UNLOCK_LEVEL ? "unlocked" : "locked (requires level " + MAP_ZOOM_UNLOCK_LEVEL + ")");
        if (level >= MINIMAP_UNLOCK_LEVEL) {
            String radiusLine = "Minimap view radius: " + MinimapZoom.radiusFor(level) + " blocks";
            return List.of(minimapLine, radiusLine, orientationLine, mapLine, waypointLine, dotsLine, zoomLine);
        }
        return List.of(minimapLine, orientationLine, mapLine, waypointLine, dotsLine, zoomLine);
    }
}
