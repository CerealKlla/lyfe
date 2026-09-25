package com.github.cerealklla.lyfe.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Covers {@link SignListener#levelCap}, the pure writer-quality-cap logic -- the rest of
 * SignListener needs a real Cartographyr-backed live server (right-click events, block/entity
 * placement) and isn't unit-testable, same limitation as {@code natural.NaturalRegionDiscovery}
 * elsewhere in this suite.
 */
class SignListenerTest {

    @Test
    void lowLevelCapsAtRelative() {
        assertEquals(LocationPrecision.RELATIVE, SignListener.levelCap(1));
        assertEquals(LocationPrecision.RELATIVE, SignListener.levelCap(9));
    }

    @Test
    void midLevelCapsAtApproximate() {
        assertEquals(LocationPrecision.APPROXIMATE, SignListener.levelCap(10));
        assertEquals(LocationPrecision.APPROXIMATE, SignListener.levelCap(24));
    }

    @Test
    void highLevelCapsAtExact() {
        assertEquals(LocationPrecision.EXACT, SignListener.levelCap(25));
        assertEquals(LocationPrecision.EXACT, SignListener.levelCap(50));
    }
}
