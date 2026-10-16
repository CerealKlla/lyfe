package com.github.cerealklla.lyfe.combat;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Per-mob "who recently hurt this" bookkeeping (explicit user spec, 2026-10-16, General section:
 * "Injuring a mob puts an assist marker for the player on that mob, which expires after 15
 * seconds"). Held as an in-memory-only NeoForge entity attachment (see {@code
 * registration.ModAttachments#MOB_ASSIST_MARKERS}) -- never persisted or synced, since a 15-second
 * combat window has no reason to survive a save/reload and nothing client-side ever needs to see
 * it. See {@link AssistTracker} for the listener that writes/reads this.
 */
public final class AssistMarkers {

    private final Map<UUID, Long> expiryGameTime = new HashMap<>();

    public void markAssist(UUID playerId, long expiryGameTime) {
        this.expiryGameTime.put(playerId, expiryGameTime);
    }

    /** Every player whose marker hasn't expired as of {@code nowGameTime}. */
    public Set<UUID> creditedPlayers(long nowGameTime) {
        Set<UUID> result = new HashSet<>();
        for (Map.Entry<UUID, Long> entry : expiryGameTime.entrySet()) {
            if (entry.getValue() > nowGameTime) {
                result.add(entry.getKey());
            }
        }
        return result;
    }
}
