package com.github.cerealklla.lyfe.huntsman;

import net.minecraft.resources.Identifier;

/**
 * Pure level-curve math for one mob type's Quarry Knowledge (explicit user spec, 2026-10-16:
 * "Each 'level' of Quarry Knoweldge will require 2x the previous level of kills of that type,
 * Level 1 starting at 5 kills... Phantoms specifically start Level 1 needing only 1 kill"). Tracks
 * a player's raw cumulative kill count per mob type ({@code huntsman.PlayerHuntsman}) and derives
 * level/progress from it here, rather than storing a separately-maintained level -- same
 * cumulative-total-is-the-source-of-truth shape as {@code skill.XpCurve#levelForXp}, just with an
 * open-ended doubling threshold instead of a fixed precomputed list (there's no natural "max
 * level" for a per-mob-type kill count the way a flat 1-50 skill curve has).
 */
public final class QuarryKnowledgeCurve {

    private static final Identifier PHANTOM = Identifier.withDefaultNamespace("phantom");

    private QuarryKnowledgeCurve() {
    }

    /** Kills needed to go from level 0 to level 1 -- 5 for any mob type, except Phantom (1). */
    public static int baseKillsForLevel1(Identifier mobType) {
        return mobType.equals(PHANTOM) ? 1 : 5;
    }

    /** Kills needed to go from level {@code (level - 1)} to {@code level} (level is 1-based). */
    public static int killsRequiredForLevel(Identifier mobType, int level) {
        return baseKillsForLevel1(mobType) * (1 << (level - 1));
    }

    /** The current level for a given raw cumulative kill count. */
    public static int levelForKills(Identifier mobType, int kills) {
        long requiredThisLevel = baseKillsForLevel1(mobType);
        long cumulative = 0;
        int level = 0;
        while (cumulative + requiredThisLevel <= kills) {
            cumulative += requiredThisLevel;
            level++;
            requiredThisLevel *= 2;
        }
        return level;
    }

    /** Total cumulative kills needed to reach {@code level} from scratch. */
    public static int cumulativeKillsForLevel(Identifier mobType, int level) {
        long requiredThisLevel = baseKillsForLevel1(mobType);
        long cumulative = 0;
        for (int i = 0; i < level; i++) {
            cumulative += requiredThisLevel;
            requiredThisLevel *= 2;
        }
        return (int) cumulative;
    }

    /** Fractional progress (0.0-1.0) toward the next level, for the transient progress bar. */
    public static double fractionToNextLevel(Identifier mobType, int kills) {
        int level = levelForKills(mobType, kills);
        int cumulativeAtLevel = cumulativeKillsForLevel(mobType, level);
        int requiredForNext = killsRequiredForLevel(mobType, level + 1);
        int progressThisLevel = kills - cumulativeAtLevel;
        return requiredForNext <= 0 ? 1.0 : Math.clamp((double) progressThisLevel / requiredForNext, 0.0, 1.0);
    }
}
