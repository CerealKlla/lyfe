package com.github.cerealklla.lyfe.knowledge;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;

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
 *
 * <p>{@code referencePos}/{@code referenceDimensionId}/{@code referenceDisplayName} (added
 * 2026-10-12, the Myssions/Cartographyr knowledge-ownership cleanup) -- "where was this knowledge
 * granted *from*," needed to actually phrase a partial hint as real text/compass direction/distance
 * ("to the northeast, 2 days' journey from Bramton") instead of a vague unanchored "somewhere."
 * Latest-grant-wins, not a history list -- a real {@code inRelationTo} list per the original design
 * sketch was considered and deliberately simplified away: in practice a place only ever has one
 * active hint source at a time (whichever quest/sign/NPC most recently told the player about it),
 * so keeping a full history buys nothing a single overwritten bundle doesn't already cover, at the
 * cost of real combinatorial text-building complexity ("direction from A, distance from B") nothing
 * in this suite needs. See {@link PlayerKnowledge#resolveDisplayText} for the consuming side.
 */
public record KnowledgeEntry(
        Set<KnowledgeFactor> locationFactors,
        boolean named,
        boolean visited,
        boolean historicallyKnown,
        Optional<BlockPos> referencePos,
        Optional<String> referenceDimensionId,
        Optional<String> referenceDisplayName
) {
    public static final Codec<KnowledgeEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.list(KnowledgeFactor.CODEC).xmap(Set::copyOf, List::copyOf)
                    .fieldOf("location_factors").forGetter(KnowledgeEntry::locationFactors),
            Codec.BOOL.fieldOf("named").forGetter(KnowledgeEntry::named),
            Codec.BOOL.fieldOf("visited").forGetter(KnowledgeEntry::visited),
            Codec.BOOL.fieldOf("historically_known").forGetter(KnowledgeEntry::historicallyKnown),
            BlockPos.CODEC.optionalFieldOf("reference_pos").forGetter(KnowledgeEntry::referencePos),
            Codec.STRING.optionalFieldOf("reference_dimension_id").forGetter(KnowledgeEntry::referenceDimensionId),
            Codec.STRING.optionalFieldOf("reference_display_name").forGetter(KnowledgeEntry::referenceDisplayName)
    ).apply(i, KnowledgeEntry::new));

    public static final KnowledgeEntry EMPTY = new KnowledgeEntry(Set.of(), false, false, false, Optional.empty(), Optional.empty(), Optional.empty());

    public KnowledgeEntry withLocationFactors(Set<KnowledgeFactor> locationFactors) {
        return new KnowledgeEntry(locationFactors, named, visited, historicallyKnown, referencePos, referenceDimensionId, referenceDisplayName);
    }

    public KnowledgeEntry withNamed(boolean named) {
        return new KnowledgeEntry(locationFactors, named, visited, historicallyKnown, referencePos, referenceDimensionId, referenceDisplayName);
    }

    public KnowledgeEntry withVisited(boolean visited) {
        return new KnowledgeEntry(locationFactors, named, visited, historicallyKnown, referencePos, referenceDimensionId, referenceDisplayName);
    }

    public KnowledgeEntry withHistoricallyKnown(boolean historicallyKnown) {
        return new KnowledgeEntry(locationFactors, named, visited, historicallyKnown, referencePos, referenceDimensionId, referenceDisplayName);
    }

    public KnowledgeEntry withReference(BlockPos newReferencePos, String newReferenceDimensionId, String newReferenceDisplayName) {
        return new KnowledgeEntry(locationFactors, named, visited, historicallyKnown,
                Optional.of(newReferencePos), Optional.of(newReferenceDimensionId), Optional.of(newReferenceDisplayName));
    }
}
