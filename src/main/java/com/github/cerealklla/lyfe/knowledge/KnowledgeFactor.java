package com.github.cerealklla.lyfe.knowledge;

import com.mojang.serialization.Codec;

/**
 * One independently-grantable piece of a player's knowledge about a location (design doc Section
 * 9.3, reworked 2026-09-25 -- see decisions.md). Replaces the old single-tier {@code
 * LocationPrecision} enum: a player's actual knowledge of a place is now the <em>set</em> of these
 * they've been granted, not a single ordinal step. {@link DistanceText#varianceFor} treats a 30%
 * "knows nothing concrete" baseline, reduced 10 points per factor known, reaching 0% (exact) once
 * all three are known.
 *
 * <p>Declaration order is deliberately "easiest to obtain" -> "hardest to obtain" ({@link
 * #DIRECTION} first, {@link #POSITION} last) -- {@code SignListener}'s writer-level cap relies on
 * this ordering to decide which factors a low-level writer is still allowed to embed.
 *
 * <p>Currently the only thing that grants factors is {@code PlayerKnowledge#markVisited}
 * (personally visiting a place grants all three at once) or reading a sign/map that already
 * embeds some. Nothing yet grants a single factor in isolation (e.g. an NPC saying "to the east of
 * here" would grant just {@link #DIRECTION}) -- that's deliberate future scope, not built yet.
 */
public enum KnowledgeFactor {
    /** A general bearing, e.g. an NPC saying "somewhere to the east." */
    DIRECTION,
    /** A general sense of how far, e.g. "about 10 days' ride." */
    DISTANCE,
    /** The precise location, e.g. having actually been there. */
    POSITION;

    public static final Codec<KnowledgeFactor> CODEC = Codec.STRING.xmap(KnowledgeFactor::valueOf, Enum::name);
}
