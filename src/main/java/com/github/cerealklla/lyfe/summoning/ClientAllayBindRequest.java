package com.github.cerealklla.lyfe.summoning;

import java.util.Optional;

import com.github.cerealklla.nuclyus.request.ClientRequestSlot;

/** Client-side bridge for "the Allay-naming screen should open" -- zero client-only imports, mirrors {@code ClientMountBindNameRequest} exactly. */
public final class ClientAllayBindRequest {

    private static final ClientRequestSlot<Integer> SLOT = new ClientRequestSlot<>();

    private ClientAllayBindRequest() {
    }

    public static void request(int allayEntityId) {
        SLOT.request(allayEntityId);
    }

    public static Optional<Integer> takePending() {
        return SLOT.takePending();
    }
}
