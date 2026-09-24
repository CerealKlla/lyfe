package com.github.cerealklla.lyfe.skill;

import java.util.List;
import java.util.OptionalLong;

/**
 * A skill's level thresholds, expressed as cumulative XP required to reach each level
 * ({@code cumulativeXpForLevel.get(0)} is the XP needed for level 1, etc.). Deliberately not a
 * shared global formula (design doc Section 3) — each skill supplies its own explicit
 * breakpoints, since e.g. Lumberjack/Miner are meant to level much faster than Historian.
 */
public record XpCurve(List<Long> cumulativeXpForLevel) {

    public XpCurve {
        cumulativeXpForLevel = List.copyOf(cumulativeXpForLevel);
        long previous = -1;
        for (long threshold : cumulativeXpForLevel) {
            if (threshold <= previous) {
                throw new IllegalArgumentException("XpCurve thresholds must be strictly increasing: " + cumulativeXpForLevel);
            }
            previous = threshold;
        }
    }

    public int maxLevel() {
        return cumulativeXpForLevel.size();
    }

    public int levelForXp(long xp) {
        int level = 0;
        for (long threshold : cumulativeXpForLevel) {
            if (xp < threshold) {
                break;
            }
            level++;
        }
        return level;
    }

    /** XP still needed to reach the next level, or empty if already at {@link #maxLevel()}. */
    public OptionalLong xpToNextLevel(long xp) {
        int level = levelForXp(xp);
        if (level >= maxLevel()) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(cumulativeXpForLevel.get(level) - xp);
    }
}
