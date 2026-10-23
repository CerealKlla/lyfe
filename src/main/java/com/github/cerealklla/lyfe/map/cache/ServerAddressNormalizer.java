package com.github.cerealklla.lyfe.map.cache;

import java.util.Locale;

/**
 * Pure, side-effect-free address normalization shared by both sides of the {@link
 * ReportServerAddressPayload}/{@link CorrectServerAddressPayload} round trip -- the client uses it
 * (via {@link MapStorageKey}) to derive a stable on-disk cache key, and the server uses the identical
 * logic to decide whether a client-reported address genuinely differs from the canonical one, rather
 * than flagging harmless case/port variation as real drift. No client-only types referenced here
 * deliberately, so the server-side comparison in {@code LyfeMod} can call it directly.
 */
public final class ServerAddressNormalizer {

    private ServerAddressNormalizer() {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim().toLowerCase(Locale.ROOT);
        return trimmed.endsWith(":25565") ? trimmed.substring(0, trimmed.length() - ":25565".length()) : trimmed;
    }
}
