package com.github.cerealklla.lyfe.geo;

import java.util.List;
import java.util.Random;

import com.github.cerealklla.cartographyr.geo.GeographicEntity;
import com.github.cerealklla.cartographyr.geo.LifecycleState;

/**
 * Composes a {@link GeographicEntity}'s final human-facing display text from its raw data fields
 * -- Cartographyr stores {@code name}/{@code designation}/{@code lifecycleState} as separate,
 * uninterpreted data (design intent: "Cartographyr owns only the registry, not rendering" -- see
 * that mod's decisions.md for the same principle applied to its Layer registry), so composing the
 * actual string a player reads is this mod's job, not Cartographyr's.
 *
 * <p>Shape: {@code "[<ruin prefix> ]<designation> of <name>"} when a designation is present (e.g.
 * "Lost Village of Stonecrest"), or just {@code "[<ruin prefix> ]<name>"} otherwise (natural
 * regions never have a designation -- see {@code Cartography#setDesignation}'s own doc). Only
 * ever called from Cartographyr-gated code ({@code knowledge.SignListener}, {@code
 * location.LocationTracker}), same as every other direct Cartographyr-API reference in this mod.
 * See decisions.md, 2026-09-25.
 */
public final class DisplayText {

    // Placeholder pool, untuned like every other magnitude in this project.
    private static final List<String> RUIN_PREFIXES = List.of("Lost", "Ruined", "Abandoned", "Forgotten", "Fallen");

    private DisplayText() {
    }

    public static String forEntity(GeographicEntity entity) {
        String name = entity.name().orElse("an unnamed place");
        String base = entity.designation().map(designation -> designation + " of " + name).orElse(name);

        if (!isRuined(entity.lifecycleState())) {
            return base;
        }
        // Seeded off the entity's own id, not a fresh Random each call, so the same ruin stays
        // described the same way every time it's read rather than flickering between prefixes.
        Random random = new Random(entity.id().value());
        String prefix = RUIN_PREFIXES.get(random.nextInt(RUIN_PREFIXES.size()));
        return prefix + " " + base;
    }

    private static boolean isRuined(LifecycleState state) {
        return state == LifecycleState.ABANDONED || state == LifecycleState.DESTROYED;
    }
}
