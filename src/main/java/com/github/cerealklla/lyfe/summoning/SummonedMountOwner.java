package com.github.cerealklla.lyfe.summoning;

import java.util.UUID;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;

/**
 * A back-reference on a summoned mount entity to the player who summoned it (design doc Section
 * 26) -- lets {@code SummonedMountDeathListener} find and update the right Summoning Stone (one
 * player's inventory) instead of scanning every online player's on every death, mirroring {@code
 * combat.AssistMarkers}'s own "plain NeoForge attachment on an arbitrary LivingEntity" precedent.
 * Persisted (a summon surviving a server restart should still be attributable), not synced -- only
 * server-side logic ever reads this.
 */
public record SummonedMountOwner(UUID playerId) {

    public static final MapCodec<SummonedMountOwner> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            UUIDUtil.CODEC.fieldOf("player_id").forGetter(SummonedMountOwner::playerId)
    ).apply(i, SummonedMountOwner::new));
}
