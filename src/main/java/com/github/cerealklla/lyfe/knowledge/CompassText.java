package com.github.cerealklla.lyfe.knowledge;

import net.minecraft.core.BlockPos;

/**
 * Plain 8-point compass direction from one point to another, Minecraft's own coordinate convention
 * (-Z is north, +X is east) -- added 2026-10-12 for {@code PlayerKnowledge#resolveDisplayText}.
 * Same formula Myssions' own {@code quest.QuestHooks#directionWord} already used inline for region
 * quests; lifted here as the one place that computes it now that direction-knowledge *resolution*
 * (not just the one-time initial grant) is Lyfe's job.
 */
public final class CompassText {

    private static final String[] POINTS = {"north", "northeast", "east", "southeast", "south", "southwest", "west", "northwest"};

    private CompassText() {
    }

    public static String wordFor(BlockPos from, BlockPos to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double bearingDegrees = (Math.toDegrees(Math.atan2(dx, -dz)) + 360.0) % 360.0;
        int index = ((int) Math.round(bearingDegrees / 45.0)) % 8;
        return POINTS[index];
    }
}
