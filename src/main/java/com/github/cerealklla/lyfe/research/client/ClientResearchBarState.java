package com.github.cerealklla.lyfe.research.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Client-side holder for every currently-active transient research-progress bar, one per recipe
 * {@link Identifier} -- mirrors {@code xpbar.client.ClientXpBarState} exactly, just keyed by recipe
 * id instead of skill id, with fractions computed directly from Research Point totals instead of
 * an XP curve (there's no curve here, just points/threshold).
 */
public final class ClientResearchBarState {

    private static final int FILL_DURATION_TICKS = 10;
    private static final int HOLD_DURATION_TICKS = 60;
    private static final int FADE_DURATION_TICKS = 10;
    private static final int TOTAL_LIFETIME_TICKS = FILL_DURATION_TICKS + HOLD_DURATION_TICKS + FADE_DURATION_TICKS;

    private static final Map<Identifier, ActiveBar> ACTIVE = new HashMap<>();

    private ClientResearchBarState() {
    }

    public static void onProgress(Identifier resultId, int oldPoints, int newPoints, int threshold) {
        double startFraction = fraction(oldPoints, threshold);
        double endFraction = fraction(newPoints, threshold);
        ActiveBar existing = ACTIVE.get(resultId);
        // Same "continue from wherever it currently sits" reasoning as ClientXpBarState -- repeated
        // rapid research attempts read as one continuously-refreshed bar, not a reset flicker.
        double continueFrom = existing != null ? existing.currentFraction() : startFraction;
        ACTIVE.put(resultId, new ActiveBar(displayNameFor(resultId), continueFrom, endFraction));
    }

    private static double fraction(int points, int threshold) {
        if (threshold <= 0) {
            return 1.0;
        }
        return Math.clamp((double) points / threshold, 0.0, 1.0);
    }

    private static String displayNameFor(Identifier resultId) {
        Item item = BuiltInRegistries.ITEM.getValue(resultId);
        return new ItemStack(item).getHoverName().getString();
    }

    public static void tick() {
        ACTIVE.values().removeIf(bar -> {
            bar.ticksAlive++;
            return bar.ticksAlive >= TOTAL_LIFETIME_TICKS;
        });
    }

    /** Sorted alphabetically by display name -- same stable/deterministic reasoning as ClientXpBarState#activeBars. */
    public static List<RenderBar> activeBars() {
        List<RenderBar> result = new ArrayList<>(ACTIVE.size());
        for (ActiveBar bar : ACTIVE.values()) {
            result.add(new RenderBar(bar.displayName, bar.currentFraction(), bar.alpha()));
        }
        result.sort(Comparator.comparing(RenderBar::displayName));
        return result;
    }

    private static final class ActiveBar {
        final String displayName;
        final double startFraction;
        final double endFraction;
        int ticksAlive;

        ActiveBar(String displayName, double startFraction, double endFraction) {
            this.displayName = displayName;
            this.startFraction = startFraction;
            this.endFraction = endFraction;
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

    public record RenderBar(String displayName, double fraction, float alpha) {
    }
}
