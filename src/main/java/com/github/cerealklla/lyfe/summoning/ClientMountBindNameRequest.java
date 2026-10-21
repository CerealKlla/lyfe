package com.github.cerealklla.lyfe.summoning;

import java.util.Optional;

import com.github.cerealklla.nuclyus.request.ClientRequestSlot;

/**
 * Client-side bridge for "the mount-naming screen should open" -- deliberately zero client-only
 * imports, same reasoning as {@code knowledge.ClientWritingRequest}: written from the common
 * {@code LyfeMod#registerPayloads} handler (must stay harmless to class-load on a dedicated
 * server), read/cleared from a genuinely client-only tick listener in {@code LyfeModClient}.
 */
public final class ClientMountBindNameRequest {

    private static final ClientRequestSlot<Integer> SLOT = new ClientRequestSlot<>();

    private ClientMountBindNameRequest() {
    }

    public static void request(int mountEntityId) {
        SLOT.request(mountEntityId);
    }

    public static Optional<Integer> takePending() {
        return SLOT.takePending();
    }
}
