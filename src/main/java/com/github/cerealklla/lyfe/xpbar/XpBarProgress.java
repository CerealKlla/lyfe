package com.github.cerealklla.lyfe.xpbar;

import com.github.cerealklla.lyfe.skill.XpCurve;

/**
 * Pure logic for the transient XP bar HUD (no live-world dependency, mirrors {@code skill.XpCurve}
 * itself) -- given a skill's curve and an XP gain's before/after totals, computes what the bar
 * should animate from and to. If the gain crossed into a new level, the bar resets to empty and
 * fills toward the new level's own progress fraction, rather than showing a fraction computed
 * against the wrong level's span.
 */
public record XpBarProgress(int level, double startFraction, double endFraction) {

    public static XpBarProgress compute(XpCurve curve, long oldXp, long newXp) {
        int oldLevel = curve.levelForXp(oldXp);
        int newLevel = curve.levelForXp(newXp);
        double endFraction = fractionWithinLevel(curve, newLevel, newXp);
        double startFraction = oldLevel == newLevel ? fractionWithinLevel(curve, oldLevel, oldXp) : 0.0;
        return new XpBarProgress(newLevel, startFraction, endFraction);
    }

    private static double fractionWithinLevel(XpCurve curve, int level, long xp) {
        if (level >= curve.maxLevel()) {
            return 1.0;
        }
        long floor = level == 0 ? 0 : curve.cumulativeXpForLevel().get(level - 1);
        long ceiling = curve.cumulativeXpForLevel().get(level);
        if (ceiling <= floor) {
            return 1.0;
        }
        double fraction = (double) (xp - floor) / (ceiling - floor);
        return Math.clamp(fraction, 0.0, 1.0);
    }
}
