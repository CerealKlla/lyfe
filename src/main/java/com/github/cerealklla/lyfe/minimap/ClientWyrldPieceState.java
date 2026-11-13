package com.github.cerealklla.lyfe.minimap;

import net.minecraft.core.BlockPos;

/**
 * Client-side holder for the latest {@link WyrldPieceSyncPayload} -- which real piece the player
 * is currently standing inside, while in TinyWyrlds' Wyrld Space dimension. Same "harmless to
 * classload on a dedicated server" shape as {@link ClientMinimapState} -- nothing here is a real
 * client-only object. Read by {@link MinimapOverlay#maybeResample} on the same resample cadence
 * the overworld path already uses, so it's never more than a tick or two stale -- no dirty-flag
 * tracking needed, since picking up the latest value every resample is already enough.
 */
public final class ClientWyrldPieceState {

    private static boolean present;
    private static BlockPos min = BlockPos.ZERO;
    private static BlockPos maxExclusive = BlockPos.ZERO;

    private ClientWyrldPieceState() {
    }

    public static void set(boolean newPresent, BlockPos newMin, BlockPos newMaxExclusive) {
        present = newPresent;
        min = newMin;
        maxExclusive = newMaxExclusive;
    }

    public static boolean present() {
        return present;
    }

    public static BlockPos min() {
        return min;
    }

    public static BlockPos maxExclusive() {
        return maxExclusive;
    }
}
