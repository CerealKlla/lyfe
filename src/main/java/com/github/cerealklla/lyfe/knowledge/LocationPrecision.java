package com.github.cerealklla.lyfe.knowledge;

import com.mojang.serialization.Codec;

/**
 * Ordered location-knowledge precision tiers (design doc Section 9.3). Ordinal order is precision
 * order -- {@link #isAtLeastAsPreciseAs} and every upgrade-only comparison in {@link PlayerKnowledge}
 * relies on {@code ordinal()}, never on declaration order changing.
 */
public enum LocationPrecision {
    RELATIVE,
    APPROXIMATE,
    EXACT;

    public static final Codec<LocationPrecision> CODEC = Codec.STRING.xmap(LocationPrecision::valueOf, Enum::name);

    public boolean isAtLeastAsPreciseAs(LocationPrecision other) {
        return this.ordinal() >= other.ordinal();
    }
}
