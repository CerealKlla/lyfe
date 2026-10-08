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
 * <p>{@code minimap.MinimapOverlay} reads the unlock-level constants: the minimap itself is invisible
 * below {@link #MINIMAP_UNLOCK_LEVEL}, its North indicator stays hidden below {@link
 * #NORTH_INDICATOR_UNLOCK_LEVEL} even once the minimap itself is unlocked. {@code map.client.MapScreen}
 * reads the remaining three: the full-screen Map ("M") does nothing at all below {@link
 * #MAP_UNLOCK_LEVEL}, settlement dots/tooltips stay hidden below {@link
 * #SETTLEMENT_DOTS_UNLOCK_LEVEL}, and scroll-wheel zoom is locked to one fixed step below {@link
 * #MAP_ZOOM_UNLOCK_LEVEL} -- all added 2026-10-07, the deferred second half of this skill's original
 * spec (the Map's own persistent backing imagery remains its own further follow-up; this pass reuses
 * the minimap's live {@code ClientTerrainSampler} instead).
 */
public final class ExpeditionistConstants {

    public static final int DISCOVERY_XP = 50;
    public static final int GENUINE_DISCOVERY_BONUS_XP = 50;

    public static final int MINIMAP_UNLOCK_LEVEL = 5;
    public static final int NORTH_INDICATOR_UNLOCK_LEVEL = 10;
    public static final int MAP_UNLOCK_LEVEL = 15;
    public static final int SETTLEMENT_DOTS_UNLOCK_LEVEL = 20;
    public static final int MAP_ZOOM_UNLOCK_LEVEL = 25;

    private ExpeditionistConstants() {
    }

    /** Current, already-computed values for the Skills screen -- see {@code skill.SkillBenefits}. */
    public static List<String> benefitLines(int level) {
        String minimapLine = "Minimap: " + (level >= MINIMAP_UNLOCK_LEVEL ? "unlocked" : "locked (requires level " + MINIMAP_UNLOCK_LEVEL + ")");
        String northLine = "North indicator: " + (level >= NORTH_INDICATOR_UNLOCK_LEVEL ? "unlocked" : "locked (requires level " + NORTH_INDICATOR_UNLOCK_LEVEL + ")");
        String mapLine = "Map (press M): " + (level >= MAP_UNLOCK_LEVEL ? "unlocked" : "locked (requires level " + MAP_UNLOCK_LEVEL + ")");
        String dotsLine = "Settlement dots on Map: " + (level >= SETTLEMENT_DOTS_UNLOCK_LEVEL ? "unlocked" : "locked (requires level " + SETTLEMENT_DOTS_UNLOCK_LEVEL + ")");
        String zoomLine = "Map zoom: " + (level >= MAP_ZOOM_UNLOCK_LEVEL ? "unlocked" : "locked (requires level " + MAP_ZOOM_UNLOCK_LEVEL + ")");
        if (level >= MINIMAP_UNLOCK_LEVEL) {
            String radiusLine = "Minimap view radius: " + MinimapZoom.radiusFor(level) + " blocks";
            return List.of(minimapLine, northLine, radiusLine, mapLine, dotsLine, zoomLine);
        }
        return List.of(minimapLine, northLine, mapLine, dotsLine, zoomLine);
    }
}
