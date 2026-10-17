package com.github.cerealklla.lyfe.socket;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * A single player's Rune Codex progress -- every distinct {@link RuneId} ever found (a bare Rune
 * item picked up, or one already socketed into a picked-up item), added 2026-10-17 for the Rune
 * Codex menu tab. No per-player "Runewords discovered" set is tracked here at all -- that's a pure
 * client-side derivation (see {@code menu.client.RuneCodexTab}'s own doc), computed from this set
 * plus the synced {@link ServerRunewordStore} rolled sequences, never stored.
 *
 * <p>{@code backfilled} guards the one-time first-login inventory/equipment scan ({@code
 * RuneBackfillListener}) so a player who already held/socketed Runes before this feature existed
 * doesn't see an empty Codex, without that scan ever re-running on a later login.
 */
public final class PlayerDiscoveredRunes {

    public static final int SCHEMA_VERSION = 1;

    public static final MapCodec<PlayerDiscoveredRunes> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.fieldOf("schema_version").forGetter(p -> p.schemaVersion),
            Codec.list(RuneId.CODEC).fieldOf("discovered_runes").forGetter(p -> java.util.List.copyOf(p.discoveredRunes)),
            Codec.BOOL.optionalFieldOf("backfilled", false).forGetter(p -> p.backfilled)
    ).apply(i, PlayerDiscoveredRunes::new));

    private final int schemaVersion;
    private final Set<RuneId> discoveredRunes;
    private boolean backfilled;

    public PlayerDiscoveredRunes() {
        this(SCHEMA_VERSION, java.util.List.of(), false);
    }

    private PlayerDiscoveredRunes(int schemaVersion, java.util.List<RuneId> discoveredRunes, boolean backfilled) {
        this.schemaVersion = schemaVersion;
        this.discoveredRunes = EnumSet.noneOf(RuneId.class);
        this.discoveredRunes.addAll(discoveredRunes);
        this.backfilled = backfilled;
    }

    public Set<RuneId> discoveredRunes() {
        return new HashSet<>(discoveredRunes);
    }

    /** @return true if {@code id} was newly added (wasn't already discovered). */
    public boolean markDiscovered(RuneId id) {
        return discoveredRunes.add(id);
    }

    public boolean isBackfilled() {
        return backfilled;
    }

    public void markBackfilled() {
        backfilled = true;
    }
}
