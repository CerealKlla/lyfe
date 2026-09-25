package com.github.cerealklla.lyfe.knowledge;

import java.util.List;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * What a player knows about a single geographic entity (design doc Section 9.3). "Unknown" is not
 * a value here -- it's the absence of any entry in {@link PlayerKnowledge}'s map. {@code named}/
 * {@code visited}/{@code historicallyKnown} are independent booleans, unrelated to {@link
 * #locationFactors}.
 *
 * <p>{@code locationFactors} replaced the old single-tier {@code LocationPrecision} field
 * 2026-09-25 (see decisions.md) -- a player's location knowledge is now the set of {@link
 * KnowledgeFactor}s they've been granted (empty set = no location knowledge at all, same meaning
 * the old {@code Optional.empty()} had).
 */
public record KnowledgeEntry(
        Set<KnowledgeFactor> locationFactors,
        boolean named,
        boolean visited,
        boolean historicallyKnown
) {
    public static final Codec<KnowledgeEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.list(KnowledgeFactor.CODEC).xmap(Set::copyOf, List::copyOf)
                    .fieldOf("location_factors").forGetter(KnowledgeEntry::locationFactors),
            Codec.BOOL.fieldOf("named").forGetter(KnowledgeEntry::named),
            Codec.BOOL.fieldOf("visited").forGetter(KnowledgeEntry::visited),
            Codec.BOOL.fieldOf("historically_known").forGetter(KnowledgeEntry::historicallyKnown)
    ).apply(i, KnowledgeEntry::new));

    public static final KnowledgeEntry EMPTY = new KnowledgeEntry(Set.of(), false, false, false);

    public KnowledgeEntry withLocationFactors(Set<KnowledgeFactor> locationFactors) {
        return new KnowledgeEntry(locationFactors, named, visited, historicallyKnown);
    }

    public KnowledgeEntry withNamed(boolean named) {
        return new KnowledgeEntry(locationFactors, named, visited, historicallyKnown);
    }

    public KnowledgeEntry withVisited(boolean visited) {
        return new KnowledgeEntry(locationFactors, named, visited, historicallyKnown);
    }

    public KnowledgeEntry withHistoricallyKnown(boolean historicallyKnown) {
        return new KnowledgeEntry(locationFactors, named, visited, historicallyKnown);
    }
}
