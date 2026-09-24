package com.github.cerealklla.lyfe.location;

import java.util.List;

/**
 * Client-side holder for the current player's location lines, updated whenever a
 * {@link LocationPayload} arrives. No client-only imports, so it's harmless if classloaded on a
 * dedicated server -- it just never gets written to there.
 */
public final class ClientLocationState {

    private static volatile List<LocationPayload.LocationLine> currentLines = List.of();

    private ClientLocationState() {
    }

    public static void set(List<LocationPayload.LocationLine> lines) {
        currentLines = lines;
    }

    public static List<LocationPayload.LocationLine> get() {
        return currentLines;
    }
}
