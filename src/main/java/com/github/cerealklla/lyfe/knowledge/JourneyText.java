package com.github.cerealklla.lyfe.knowledge;

/**
 * Coarse, in-world-flavor "how far" phrasing for a {@link KnowledgeFactor#DISTANCE}-only hint --
 * added 2026-10-12, explicit user spec: "200 blocks = 1 'day'." Deliberately separate from {@code
 * DistanceText} (feet/miles, used for a sign/map's own knowledge-fuzzed precision display) -- that
 * one answers "how far is this, as a real-world measurement," this one answers "how far does this
 * *feel* for a vague travel hint," which is a different unit entirely and shouldn't be conflated.
 */
public final class JourneyText {

    private static final double BLOCKS_PER_DAY = 200.0;

    private JourneyText() {
    }

    public static String describe(double distanceBlocks) {
        int days = (int) Math.ceil(distanceBlocks / BLOCKS_PER_DAY);
        if (days <= 0) {
            return "less than a day's journey";
        }
        if (days == 1) {
            return "about a day's journey";
        }
        return "about " + days + " days' journey";
    }
}
