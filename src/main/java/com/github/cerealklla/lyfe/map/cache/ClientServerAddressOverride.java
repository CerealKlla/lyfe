package com.github.cerealklla.lyfe.map.cache;

/**
 * Client-only, session-scoped override for {@link MapStorageKey}'s own {@code worldKey} address --
 * set when the server corrects a drifted {@link ReportServerAddressPayload} (see {@code
 * CorrectServerAddressPayload}'s own doc), always takes priority over {@code ServerData#ip} while
 * present. Cleared on logout alongside this mod's other per-session client state (same lifecycle as
 * {@code ClientWaypointState}) so a later connection to a genuinely different server never inherits
 * a stale override.
 */
public final class ClientServerAddressOverride {

    private static volatile String override;

    private ClientServerAddressOverride() {
    }

    public static String get() {
        return override;
    }

    public static void set(String address) {
        override = address;
    }

    public static void clear() {
        override = null;
    }
}
