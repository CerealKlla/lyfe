package com.github.cerealklla.lyfe.huntsman.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.github.cerealklla.lyfe.huntsman.QuarryKnowledgeCurve;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

/**
 * Client-side holder for every currently-active transient Quarry Knowledge progress bar, one per
 * mob type {@link Identifier} -- mirrors {@code research.client.ClientResearchBarState} exactly
 * (same fill/hold/fade timing), per explicit user request to reuse Researcher's own visual style,
 * just keyed by entity type and with fractions computed via {@link QuarryKnowledgeCurve} instead
 * of a flat Research Points/threshold ratio.
 */
public final class ClientHuntsmanBarState {

    private static final int FILL_DURATION_TICKS = 10;
    private static final int HOLD_DURATION_TICKS = 60;
    private static final int FADE_DURATION_TICKS = 10;
    private static final int TOTAL_LIFETIME_TICKS = FILL_DURATION_TICKS + HOLD_DURATION_TICKS + FADE_DURATION_TICKS;

    private static final Map<Identifier, ActiveBar> ACTIVE = new HashMap<>();

    private ClientHuntsmanBarState() {
    }

    public static void onProgress(Identifier mobType, int oldKills, int newKills) {
        double startFraction = QuarryKnowledgeCurve.fractionToNextLevel(mobType, oldKills);
        double endFraction = QuarryKnowledgeCurve.fractionToNextLevel(mobType, newKills);
        ActiveBar existing = ACTIVE.get(mobType);
        // Same "continue from wherever it currently sits" reasoning as ClientResearchBarState --
        // repeated rapid kills read as one continuously-refreshed bar, not a reset flicker. A level-up
        // (fraction wraps back toward 0) still reads correctly since currentFraction() just animates
        // straight from the old value to the new one, whichever direction that is.
        double continueFrom = existing != null ? existing.currentFraction() : startFraction;
        ACTIVE.put(mobType, new ActiveBar(displayNameFor(mobType), continueFrom, endFraction,
                QuarryKnowledgeCurve.levelForKills(mobType, newKills)));
    }

    private static String displayNameFor(Identifier mobType) {
        var entityType = BuiltInRegistries.ENTITY_TYPE.getValue(mobType);
        return entityType.getDescription().getString();
    }

    public static void tick() {
        ACTIVE.values().removeIf(bar -> {
            bar.ticksAlive++;
            return bar.ticksAlive >= TOTAL_LIFETIME_TICKS;
        });
    }

    /** Sorted alphabetically by display name -- same stable/deterministic reasoning as ClientResearchBarState#activeBars. */
    public static List<RenderBar> activeBars() {
        List<RenderBar> result = new ArrayList<>(ACTIVE.size());
        for (ActiveBar bar : ACTIVE.values()) {
            result.add(new RenderBar(bar.displayName, bar.currentFraction(), bar.alpha(), bar.level));
        }
        result.sort(Comparator.comparing(RenderBar::displayName));
        return result;
    }

    private static final class ActiveBar {
        final String displayName;
        final double startFraction;
        final double endFraction;
        final int level;
        int ticksAlive;

        ActiveBar(String displayName, double startFraction, double endFraction, int level) {
            this.displayName = displayName;
            this.startFraction = startFraction;
            this.endFraction = endFraction;
            this.level = level;
        }

        double currentFraction() {
            if (ticksAlive >= FILL_DURATION_TICKS) {
                return endFraction;
            }
            double t = (double) ticksAlive / FILL_DURATION_TICKS;
            return startFraction + (endFraction - startFraction) * t;
        }

        float alpha() {
            int fadeStartTick = FILL_DURATION_TICKS + HOLD_DURATION_TICKS;
            if (ticksAlive < fadeStartTick) {
                return 1.0f;
            }
            float fadeProgress = (float) (ticksAlive - fadeStartTick) / FADE_DURATION_TICKS;
            return Math.max(0.0f, 1.0f - fadeProgress);
        }
    }

    public record RenderBar(String displayName, double fraction, float alpha, int level) {
    }
}
