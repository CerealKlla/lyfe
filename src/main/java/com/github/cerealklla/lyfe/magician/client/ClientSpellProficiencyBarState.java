package com.github.cerealklla.lyfe.magician.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.github.cerealklla.lyfe.magician.SpellProficiencyCurve;

import net.minecraft.resources.Identifier;

/**
 * Client-side holder for every currently-active transient Spell Proficiency progress bar, one per
 * spell {@link Identifier} -- mirrors {@code huntsman.client.ClientHuntsmanBarState} exactly (same
 * fill/hold/fade timing and "continue from wherever it currently sits" reasoning), per explicit
 * user request to show Spell Proficiency progress the same visual way Quarry Knowledge and every
 * other skill-adjacent mechanic already does.
 */
public final class ClientSpellProficiencyBarState {

    private static final int FILL_DURATION_TICKS = 10;
    private static final int HOLD_DURATION_TICKS = 60;
    private static final int FADE_DURATION_TICKS = 10;
    private static final int TOTAL_LIFETIME_TICKS = FILL_DURATION_TICKS + HOLD_DURATION_TICKS + FADE_DURATION_TICKS;

    private static final Map<Identifier, ActiveBar> ACTIVE = new HashMap<>();

    private ClientSpellProficiencyBarState() {
    }

    public static void onProgress(Identifier spellId, int oldProgress, int newProgress) {
        double startFraction = SpellProficiencyCurve.fractionToNextLevel(oldProgress);
        double endFraction = SpellProficiencyCurve.fractionToNextLevel(newProgress);
        ActiveBar existing = ACTIVE.get(spellId);
        double continueFrom = existing != null ? existing.currentFraction() : startFraction;
        ACTIVE.put(spellId, new ActiveBar(displayNameFor(spellId), continueFrom, endFraction,
                SpellProficiencyCurve.levelForProgress(newProgress)));
    }

    /** Mirrors {@code menu.client.SpellsTab#displayName} -- same small switch, kept local since this is the only other place a spell's name is ever shown outside that menu. */
    private static String displayNameFor(Identifier spellId) {
        return switch (spellId.getPath()) {
            case "spark_of_life" -> "Spark of Life";
            default -> spellId.getPath();
        };
    }

    public static void tick() {
        ACTIVE.values().removeIf(bar -> {
            bar.ticksAlive++;
            return bar.ticksAlive >= TOTAL_LIFETIME_TICKS;
        });
    }

    /** Sorted alphabetically by display name -- same stable/deterministic reasoning as ClientHuntsmanBarState#activeBars. */
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
