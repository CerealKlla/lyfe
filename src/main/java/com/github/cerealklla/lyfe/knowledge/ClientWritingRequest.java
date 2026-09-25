package com.github.cerealklla.lyfe.knowledge;

import java.util.List;
import java.util.Optional;

/**
 * Client-side bridge for "a writing dialog should open" -- deliberately zero client-only imports,
 * same reasoning as {@code location.ClientLocationState}: this is written from the common {@code
 * LyfeMod#registerPayloads} handler (which must stay harmless to class-load on a dedicated
 * server), and read/cleared from a genuinely client-only tick listener in {@code LyfeModClient}
 * that's the only place actually allowed to touch {@code Minecraft}/{@code Screen}.
 */
public final class ClientWritingRequest {

    public record Request(WritingTarget target, List<OpenWritingScreenPayload.KnownPlace> knownPlaces) {
    }

    private static volatile Request pending;

    private ClientWritingRequest() {
    }

    public static void request(Request request) {
        pending = request;
    }

    /** Clears and returns the pending request, if any -- a poll-once handoff, not a persistent state. */
    public static Optional<Request> takePending() {
        Request request = pending;
        pending = null;
        return Optional.ofNullable(request);
    }
}
