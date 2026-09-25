package com.github.cerealklla.lyfe.knowledge;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * A single player's persisted world knowledge (design doc Section 9.3) -- the real thing
 * {@code .location.LocationTracker} was an explicit stand-in for until now (see decisions.md,
 * "Location overlay moved from Cartographyr into Lyfe"). Lyfe owns this entirely; Cartographyr
 * only ever supplies read-only world-truth queries, never anything about what a specific player
 * has learned (Section 9.3's data-ownership split, and Cartographyr's own decisions.md removing
 * "Player Knowledge" from its scope for exactly this reason).
 *
 * <p>Places are keyed by the raw {@code long} underlying Cartographyr's {@code EntityId} (rather
 * than {@code EntityId} itself) very deliberately: this class is registered unconditionally in
 * {@code ModAttachments} (same as every other attachment), so its {@code CODEC}'s static
 * initializer runs regardless of whether Cartographyr is loaded. Referencing Cartographyr's
 * {@code EntityId.CODEC} there would force-load Cartographyr's classes on a server that doesn't
 * have Cartographyr installed at all, breaking the soft-dependency contract (design doc Section
 * 8) at a much subtler point than the already-established "only construct/register the consumer
 * class conditionally" pattern covers -- that pattern protects against *construction*, not a
 * *static initializer* evaluated by an unconditionally-registered attachment/item/payload.
 * Callers that actually have an {@code EntityId} (only ever {@code location.LocationTracker} and
 * {@code knowledge.SignListener}, both already gated behind {@code ModList.isLoaded("cartographyr")})
 * convert via {@code EntityId#value()} at the call site.
 *
 * <p>Not synced to the client -- nothing client-side reads this directly. Both consumers
 * ({@code location.LocationTracker}'s overlay gating and {@code knowledge.SignListener}'s
 * read/write XP logic) run server-side and only ever send already-resolved text to the client,
 * same as before this class existed.
 */
public final class PlayerKnowledge {

    public static final int SCHEMA_VERSION = 1;

    // unboundedMap requires a string-encoding key codec (NBT/JSON object keys are strings) --
    // Codec.LONG alone fails with "Not a string" on encode. Mirrors how Cartographyr's own
    // EntityId.CODEC is string-based for exactly this reason.
    private static final Codec<Long> PLACE_ID_CODEC = Codec.STRING.xmap(Long::parseLong, String::valueOf);

    public static final MapCodec<PlayerKnowledge> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.fieldOf("schema_version").forGetter(p -> p.schemaVersion),
            Codec.unboundedMap(PLACE_ID_CODEC, KnowledgeEntry.CODEC).fieldOf("entries").forGetter(p -> p.entries)
    ).apply(i, PlayerKnowledge::new));

    private final int schemaVersion;
    private final Map<Long, KnowledgeEntry> entries;

    public PlayerKnowledge() {
        this(SCHEMA_VERSION, new HashMap<>());
    }

    private PlayerKnowledge(int schemaVersion, Map<Long, KnowledgeEntry> entries) {
        this.schemaVersion = schemaVersion;
        this.entries = new HashMap<>(entries);
    }

    public Optional<KnowledgeEntry> get(long placeId) {
        return Optional.ofNullable(entries.get(placeId));
    }

    /** Read-only view, for populating the writing screen's known-places list. */
    public Map<Long, KnowledgeEntry> entries() {
        return Map.copyOf(entries);
    }

    /**
     * Merges {@code factors} into whatever the player already knows about {@code placeId} --
     * strictly additive (union), never removes a factor already known. Replaced {@code
     * upgradeLocationPrecision} 2026-09-25 (see decisions.md): a player's location knowledge is now
     * the set of independently-grantable {@link KnowledgeFactor}s they have, not a single ordinal
     * tier, so "did this improve their knowledge" is "did the union actually gain anything new,"
     * not "is the new value higher than the old one."
     *
     * @return true if this actually added at least one new factor (a brand-new entry counts).
     */
    public boolean learnLocationFactors(long placeId, Set<KnowledgeFactor> factors) {
        KnowledgeEntry current = entries.getOrDefault(placeId, KnowledgeEntry.EMPTY);
        Set<KnowledgeFactor> merged = new HashSet<>(current.locationFactors());
        boolean changed = merged.addAll(factors);
        if (changed) {
            entries.put(placeId, current.withLocationFactors(Set.copyOf(merged)));
        }
        return changed;
    }

    /**
     * Marks an entity visited, and force-grants every {@link KnowledgeFactor} regardless of what
     * was already known -- standing somewhere means you know exactly where "there" is, so physical
     * presence is authoritative over anything a sign/map ever told you.
     *
     * @return true if anything actually changed (first visit, or a factor was actually added).
     */
    public boolean markVisited(long placeId) {
        KnowledgeEntry current = entries.getOrDefault(placeId, KnowledgeEntry.EMPTY);
        boolean alreadyExact = current.locationFactors().containsAll(EnumSet.allOf(KnowledgeFactor.class));
        boolean changed = !current.visited() || !alreadyExact;
        if (changed) {
            KnowledgeEntry updated = current.withVisited(true).withLocationFactors(EnumSet.allOf(KnowledgeFactor.class));
            entries.put(placeId, updated);
        }
        return changed;
    }

    public boolean markNamed(long placeId) {
        KnowledgeEntry current = entries.getOrDefault(placeId, KnowledgeEntry.EMPTY);
        if (current.named()) {
            return false;
        }
        entries.put(placeId, current.withNamed(true));
        return true;
    }

    public boolean markHistoricallyKnown(long placeId) {
        KnowledgeEntry current = entries.getOrDefault(placeId, KnowledgeEntry.EMPTY);
        if (current.historicallyKnown()) {
            return false;
        }
        entries.put(placeId, current.withHistoricallyKnown(true));
        return true;
    }
}
