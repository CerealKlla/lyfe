package com.github.cerealklla.lyfe.location;

import java.util.Optional;

/**
 * Client-side holder for the current player's location lines, updated whenever a
 * {@link LocationPayload} arrives. No client-only imports, so it's harmless if classloaded on a
 * dedicated server -- it just never gets written to there.
 *
 * <p>Gained {@code kingdomLine}, 2026-11-15 -- see {@link LocationPayload}'s own doc.
 */
public final class ClientLocationState {

    private static volatile Optional<String> currentKingdomLine = Optional.empty();
    private static volatile Optional<String> currentLine1 = Optional.empty();
    private static volatile Optional<String> currentLine2 = Optional.empty();

    private ClientLocationState() {
    }

    public static void set(Optional<String> kingdomLine, Optional<String> line1, Optional<String> line2) {
        currentKingdomLine = kingdomLine;
        currentLine1 = line1;
        currentLine2 = line2;
    }

    public static Optional<String> getKingdomLine() {
        return currentKingdomLine;
    }

    public static Optional<String> getLine1() {
        return currentLine1;
    }

    public static Optional<String> getLine2() {
        return currentLine2;
    }
}
