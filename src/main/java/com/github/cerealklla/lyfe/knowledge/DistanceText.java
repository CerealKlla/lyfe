package com.github.cerealklla.lyfe.knowledge;

import java.util.Random;

/**
 * Formats a block distance as imperial feet/miles (design doc Section 9.1, added 2026-09-25 --
 * see decisions.md), fuzzed by a variance ratio for imprecise knowledge. A block is treated as
 * 1x1 meter (the user's explicit spec), converted to feet/miles for display -- vanilla's own
 * distance-ish UI (e.g. the debug screen) uses raw blocks, but this project's Cartographyr skill
 * is meant to read as an in-world measurement system, not a block count.
 */
final class DistanceText {

    private static final double FEET_PER_BLOCK = 3.28084;
    private static final double FEET_PER_MILE = 5280.0;

    private DistanceText() {
    }

    /**
     * @param blocks    the real distance in blocks
     * @param variance  fraction in [0, 1] -- 0 means exact (no "~", no fuzz); e.g. 0.2 means the
     *                  displayed distance is randomly off by up to +/-20% of the real value
     * @param random    fuzz source -- callers pass a fresh {@link Random} so the fuzzed value is
     *                  rolled once and baked into the sign, not re-randomized on every read
     */
    static String format(double blocks, double variance, Random random) {
        double fuzzedBlocks = blocks;
        if (variance > 0) {
            double fuzzFactor = 1.0 + (random.nextDouble() * 2 - 1) * variance;
            fuzzedBlocks = Math.max(0, blocks * fuzzFactor);
        }

        double feet = fuzzedBlocks * FEET_PER_BLOCK;
        String formatted = feet >= FEET_PER_MILE
                ? String.format("%.1f mi", feet / FEET_PER_MILE)
                : Math.round(feet) + " ft";
        return variance > 0 ? "~" + formatted : formatted;
    }

    /**
     * How fuzzy a distance reading is, by {@link LocationPrecision}. Interim mapping (see
     * decisions.md, 2026-09-25) onto the user's described future model -- a 30% "knows nothing
     * concrete" baseline, reduced 10 points for each of three independently-grantable knowledge
     * components (general direction, general distance, exact position) -- collapsed onto today's
     * single three-tier {@link LocationPrecision} since nothing yet grants those components
     * separately: {@code RELATIVE} reads as "direction only" (1 of 3, 20% variance), {@code
     * APPROXIMATE} as "direction and distance, not exact position" (2 of 3, 10%), {@code EXACT} as
     * all three (0%, no fuzz at all). Revisit once real partial-knowledge sources (NPC dialogue,
     * books) exist and can set the three components independently.
     */
    static double varianceFor(LocationPrecision precision) {
        return switch (precision) {
            case RELATIVE -> 0.20;
            case APPROXIMATE -> 0.10;
            case EXACT -> 0.0;
        };
    }
}
