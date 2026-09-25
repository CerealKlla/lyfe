package com.github.cerealklla.lyfe.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.github.cerealklla.cartographyr.geo.Geometry;

import net.minecraft.world.level.block.state.properties.RotationSegment;

/**
 * Covers {@link SignListener#levelCap} (the pure writer-quality-cap logic) and the pure geometry
 * helpers behind the sign-rotation/arrow math ({@link SignListener#isRightOf}, {@link
 * SignListener#bearingDegrees}) -- the rest of SignListener needs a real Cartographyr-backed live
 * server (right-click events, block/entity placement) and isn't unit-testable, same limitation as
 * {@code natural.NaturalRegionDiscovery} elsewhere in this suite. The rotation math itself is
 * flagged in decisions.md as not yet empirically verified in-game; these tests only confirm the
 * one part with independent ground truth ({@code bearingDegrees} against {@link RotationSegment}'s
 * own documented NORTH_0/EAST_90/SOUTH_180/WEST_270 constants) and simple self-consistency for the
 * left/right test.
 */
class SignListenerTest {

    @Test
    void bearingDegreesMatchesRotationSegmentsDocumentedCardinalConstants() {
        // North = -Z, East = +X, South = +Z, West = -X (MC convention).
        assertEquals(0.0, SignListener.bearingDegrees(0, -1), 0.001);
        assertEquals(90.0, SignListener.bearingDegrees(1, 0), 0.001);
        assertEquals(180.0, SignListener.bearingDegrees(0, 1), 0.001);
        assertEquals(270.0, SignListener.bearingDegrees(-1, 0), 0.001);

        assertEquals(0, RotationSegment.convertToSegment((float) SignListener.bearingDegrees(0, -1)));
        assertEquals(4, RotationSegment.convertToSegment((float) SignListener.bearingDegrees(1, 0)));
        assertEquals(8, RotationSegment.convertToSegment((float) SignListener.bearingDegrees(0, 1)));
        assertEquals(12, RotationSegment.convertToSegment((float) SignListener.bearingDegrees(-1, 0)));
    }

    @Test
    void isRightOfMatchesTheNorthFacingEastTargetExample() {
        // Facing north (0,-1), something due east (1,0) must read as "right" -- the concrete
        // example the formula was derived and checked against.
        assertTrue(SignListener.isRightOf(0, -1, 1, 0));
        // Symmetric: facing north, something due west is to the left.
        assertFalse(SignListener.isRightOf(0, -1, -1, 0));
    }

    @Test
    void isRightOfIsConsistentWhenFacingIsReversed() {
        // Facing south (0,1) instead of north -- east should now read as "left".
        assertFalse(SignListener.isRightOf(0, 1, 1, 0));
    }

    /**
     * The rotation-vs-front-normal offset fix, locked in against a concrete, independently-known
     * fact about vanilla: a player facing south who places a sign directly in front of them (south
     * of themselves) can read it immediately -- meaning the front must face north (toward the
     * placer). Vanilla's own {@code StandingSignBlock#getStateForPlacement} computes {@code
     * rotation = yRot(0) + 180 = 180 (south)} for that exact case -- i.e. the ROTATION value is
     * SOUTH even though the required front-facing direction is NORTH. This test reconstructs that
     * same "desired front = north" case via {@code frontFacingBearingDegrees} (target due west,
     * "right" case, algebraically produces a north-facing front before the offset) and asserts it
     * also lands on segment 8 (south) -- matching vanilla, not the naive un-offset formula.
     */
    @Test
    void frontFacingBearingMatchesVanillasKnownCorrectRotationOffset() {
        double degrees = SignListener.frontFacingBearingDegrees(-1, 0, true);
        assertEquals(8, RotationSegment.convertToSegment((float) degrees));
    }

    /**
     * A same-day playtest report claiming this needed mirroring turned out to be a false alarm
     * (the reporter had gotten turned around at night) -- see decisions.md, 2026-09-24 (the
     * "retraction" entry). Locks in the original, correct mapping.
     */
    @Test
    void arrowForMatchesTargetIsRightDirectly() {
        assertEquals("--->", SignListener.arrowFor(true));
        assertEquals("<---", SignListener.arrowFor(false));
    }

    /** A single-chunk footprint (16 blocks) comfortably fits in the smallest scale (grid 128). */
    @Test
    void mapScaleForSmallFootprintUsesSmallestScale() {
        Geometry.Bounds bounds = new Geometry.Bounds(0, 0, 15, 15);
        assertEquals((byte) 0, SignListener.mapScaleFor(bounds));
    }

    /** A ~160-block-wide footprint needs grid >= 480, which scale 2 (grid 512) is the smallest to satisfy. */
    @Test
    void mapScaleForMediumFootprintPicksSmallestSufficientScale() {
        Geometry.Bounds bounds = new Geometry.Bounds(0, 0, 159, 159);
        assertEquals((byte) 2, SignListener.mapScaleFor(bounds));
    }

    /** A huge footprint that no vanilla scale can triple-cover falls back to the max scale (4). */
    @Test
    void mapScaleForHugeFootprintCapsAtMaxScale() {
        Geometry.Bounds bounds = new Geometry.Bounds(0, 0, 1599, 1599);
        assertEquals((byte) 4, SignListener.mapScaleFor(bounds));
    }

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
