package com.github.cerealklla.lyfe.knowledge;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * What a player knows about a single geographic entity (design doc Section 9.3). "Unknown" is not
 * a value here -- it's the absence of any entry in {@link PlayerKnowledge}'s map. {@code named}/
 * {@code visited}/{@code historicallyKnown} are independent booleans, not part of the
 * {@link LocationPrecision} ordering.
 */
public record KnowledgeEntry(
        Optional<LocationPrecision> locationPrecision,
        boolean named,
        boolean visited,
        boolean historicallyKnown
) {
    public static final Codec<KnowledgeEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
            LocationPrecision.CODEC.optionalFieldOf("location_precision").forGetter(KnowledgeEntry::locationPrecision),
            Codec.BOOL.fieldOf("named").forGetter(KnowledgeEntry::named),
            Codec.BOOL.fieldOf("visited").forGetter(KnowledgeEntry::visited),
            Codec.BOOL.fieldOf("historically_known").forGetter(KnowledgeEntry::historicallyKnown)
    ).apply(i, KnowledgeEntry::new));

    public static final KnowledgeEntry EMPTY = new KnowledgeEntry(Optional.empty(), false, false, false);

    public KnowledgeEntry withLocationPrecision(LocationPrecision precision) {
        return new KnowledgeEntry(Optional.of(precision), named, visited, historicallyKnown);
    }

    public KnowledgeEntry withNamed(boolean named) {
        return new KnowledgeEntry(locationPrecision, named, visited, historicallyKnown);
    }

    public KnowledgeEntry withVisited(boolean visited) {
        return new KnowledgeEntry(locationPrecision, named, visited, historicallyKnown);
    }

    public KnowledgeEntry withHistoricallyKnown(boolean historicallyKnown) {
        return new KnowledgeEntry(locationPrecision, named, visited, historicallyKnown);
    }
}
