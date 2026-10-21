package com.github.cerealklla.lyfe.summoning;

import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A Summoning Stone's persisted state (design doc Section 26): the bound mount's full captured
 * snapshot ({@code Entity#saveWithoutId} output, minus UUID by construction -- see {@code
 * MountBindListener}'s own doc), a wall-clock cooldown end (mirrors {@code recallcinite.
 * RecallciniteData}'s own reasoning: a world's game-time stops advancing whenever the server
 * process itself is stopped, so a death-triggered cooldown must be anchored to real elapsed time,
 * not game ticks, or it reads as "paused" across a restart), and the currently-summoned mount's own
 * UUID (if one is out right now).
 */
public record SummoningStoneData(CompoundTag savedState, long cooldownEndEpochMillis, Optional<UUID> summonedMountId) {

    public static final Codec<SummoningStoneData> CODEC = RecordCodecBuilder.create(i -> i.group(
            CompoundTag.CODEC.fieldOf("saved_state").forGetter(SummoningStoneData::savedState),
            Codec.LONG.optionalFieldOf("cooldown_end_epoch_millis", 0L).forGetter(SummoningStoneData::cooldownEndEpochMillis),
            UUIDUtil.CODEC.optionalFieldOf("summoned_mount_id").forGetter(SummoningStoneData::summonedMountId)
    ).apply(i, SummoningStoneData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, SummoningStoneData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.COMPOUND_TAG, SummoningStoneData::savedState,
            ByteBufCodecs.VAR_LONG, SummoningStoneData::cooldownEndEpochMillis,
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), SummoningStoneData::summonedMountId,
            SummoningStoneData::new);

    public boolean onCooldown(long currentEpochMillis) {
        return currentEpochMillis < cooldownEndEpochMillis;
    }

    public SummoningStoneData withSavedState(CompoundTag newState) {
        return new SummoningStoneData(newState, cooldownEndEpochMillis, summonedMountId);
    }

    public SummoningStoneData withSummonedMountId(Optional<UUID> id) {
        return new SummoningStoneData(savedState, cooldownEndEpochMillis, id);
    }

    public SummoningStoneData withCooldownEndEpochMillis(long end) {
        return new SummoningStoneData(savedState, end, summonedMountId);
    }
}
