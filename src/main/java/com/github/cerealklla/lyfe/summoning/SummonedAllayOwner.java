package com.github.cerealklla.lyfe.summoning;

import java.util.UUID;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;

/**
 * A back-reference on a summoned Allay entity to whichever player currently holds the bottle it
 * came from (design doc Section 27) -- direct mirror of {@link SummonedMountOwner}. Stamped fresh
 * on every real summon from whoever actually triggers it (the current holder, not a frozen
 * "original binder") -- this is also exactly what makes possession-based ownership/resale work for
 * free, since nothing here ever outlives one summon/dispel cycle.
 */
public record SummonedAllayOwner(UUID playerId) {

    public static final MapCodec<SummonedAllayOwner> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            UUIDUtil.CODEC.fieldOf("player_id").forGetter(SummonedAllayOwner::playerId)
    ).apply(i, SummonedAllayOwner::new));
}
