package com.github.cerealklla.lyfe.xpbar.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.github.cerealklla.lyfe.api.Lyfe;
import com.github.cerealklla.lyfe.skill.SkillId;
import com.github.cerealklla.lyfe.xpbar.XpBarProgress;

/**
 * Client-side holder for every currently-active transient XP bar, one per {@link SkillId}. Updated
 * whenever an {@code XpGainPayload} arrives ({@link #onXpGain}); advanced once per client tick via
 * {@link #tick()} (called from {@code LyfeModClient}'s existing per-tick method). No client-only
 * imports beyond this package's own render-data record, so it's harmless if classloaded on a
 * dedicated server -- same convention as {@code location.ClientLocationState}. Single-threaded
 * (client main thread only, both for the payload handler and the tick) -- a plain {@code HashMap}
 * is safe, no concurrent structure needed.
 */
public final class ClientXpBarState {

    // All placeholder/tunable except HOLD_DURATION_TICKS, which is the user's own explicit spec
    // ("fade away after 3 seconds").
    private static final int FILL_DURATION_TICKS = 10;
    private static final int HOLD_DURATION_TICKS = 60;
    private static final int FADE_DURATION_TICKS = 10;
    private static final int TOTAL_LIFETIME_TICKS = FILL_DURATION_TICKS + HOLD_DURATION_TICKS + FADE_DURATION_TICKS;

    private static final Map<SkillId, ActiveBar> ACTIVE = new HashMap<>();

    private ClientXpBarState() {
    }

    /**
     * Looks up the skill's definition/curve itself (safe client-side -- {@code SkillRegistry} is
     * populated identically on both sides at mod construction, no live-world data needed) rather
     * than having the payload carry display name/level/fractions already computed, since all of
     * that is derivable from the same two raw XP totals on either side.
     */
    public static void onXpGain(SkillId skillId, long oldXp, long newXp) {
        Lyfe.getSkillDefinition(skillId).ifPresent(def -> {
            XpBarProgress progress = XpBarProgress.compute(def.xpCurve(), oldXp, newXp);
            ActiveBar existing = ACTIVE.get(skillId);
            // Continue from wherever the bar currently sits, not the stale start of a bar that's
            // already mid-animation -- repeated rapid gains (e.g. chopping several logs in a row)
            // read as one continuously-refreshed bar, not a reset-and-restart flicker.
            double continueFrom = existing != null ? existing.currentFraction() : progress.startFraction();
            ACTIVE.put(skillId, new ActiveBar(def.displayName(), progress.level(), continueFrom, progress.endFraction()));
        });
    }

    public static void tick() {
        ACTIVE.values().removeIf(bar -> {
            bar.ticksAlive++;
            return bar.ticksAlive >= TOTAL_LIFETIME_TICKS;
        });
    }

    /** Sorted alphabetically by display name -- stable and deterministic as bars independently appear/expire. */
    public static List<RenderBar> activeBars() {
        List<RenderBar> result = new ArrayList<>(ACTIVE.size());
        for (ActiveBar bar : ACTIVE.values()) {
            result.add(new RenderBar(bar.displayName, bar.level, bar.currentFraction(), bar.alpha()));
        }
        result.sort(Comparator.comparing(RenderBar::displayName));
        return result;
    }

    private static final class ActiveBar {
        final String displayName;
        final int level;
        final double startFraction;
        final double endFraction;
        int ticksAlive;

        ActiveBar(String displayName, int level, double startFraction, double endFraction) {
            this.displayName = displayName;
            this.level = level;
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

    public record RenderBar(String displayName, int level, double fraction, float alpha) {
    }
}
