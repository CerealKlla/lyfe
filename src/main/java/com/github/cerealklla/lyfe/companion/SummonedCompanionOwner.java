package com.github.cerealklla.lyfe.companion;

import java.util.UUID;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;

/**
 * A back-reference on a summoned Tavern Companion entity to whichever player currently holds the
 * token it came from -- direct mirror of {@code summoning.SummonedAllayOwner}. Stamped fresh on
 * every real summon from whoever actually triggers it, so possession-based ownership/resale works
 * for free, same as every other summon type in this mod.
 */
public record SummonedCompanionOwner(UUID playerId) {

    public static final MapCodec<SummonedCompanionOwner> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            UUIDUtil.CODEC.fieldOf("player_id").forGetter(SummonedCompanionOwner::playerId)
    ).apply(i, SummonedCompanionOwner::new));
}
