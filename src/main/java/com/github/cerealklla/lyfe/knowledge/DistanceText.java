package com.github.cerealklla.lyfe.knowledge;

import java.util.Random;
import java.util.Set;

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

    // A 30% "knows nothing concrete" baseline, reduced 10 points per KnowledgeFactor known --
    // reaches 0% (exact) once all three are known. See decisions.md, 2026-09-25.
    private static final double BASE_VARIANCE = 0.30;
    private static final double VARIANCE_PER_FACTOR = 0.10;

    /** How fuzzy a distance reading is, given the set of {@link KnowledgeFactor}s known about a place. */
    static double varianceFor(Set<KnowledgeFactor> factors) {
        return Math.max(0.0, BASE_VARIANCE - VARIANCE_PER_FACTOR * factors.size());
    }
}
