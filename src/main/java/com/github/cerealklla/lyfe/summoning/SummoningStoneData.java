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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.TagValueOutput;

import com.github.cerealklla.lyfe.LyfeMod;

/**
 * A Summoning Stone's persisted state (design doc Section 26): the bound mount's full captured
 * snapshot, a wall-clock cooldown end (mirrors {@code recallcinite.RecallciniteData}'s own
 * reasoning: a world's game-time stops advancing whenever the server process itself is stopped, so
 * a death-triggered cooldown must be anchored to real elapsed time, not game ticks, or it reads as
 * "paused" across a restart), and the currently-summoned mount's own UUID (if one is out right now).
 */
public record SummoningStoneData(CompoundTag savedState, long cooldownEndEpochMillis, Optional<UUID> summonedMountId) {

    /**
     * Captures {@code mount}'s complete state for later re-spawning via {@code
     * EntityType#loadEntityRecursive} -- used both at bind time and whenever the distance ticker
     * re-captures a summoned mount's current state before despawning it.
     *
     * <p><b>Real bug fixed 2026-10-21</b>: this used to call {@code Entity#saveWithoutId} directly
     * and assumed (per its own now-wrong doc comment, and an earlier research pass that was never
     * actually re-verified against the real decompiled source before being trusted) that doing so
     * omitted the entity's UUID. Confirmed by reading the real decompiled {@code Entity.java}:
     * {@code saveWithoutId} is "without the entity *type* id" (the {@code "id"} registry-name key,
     * normally written by {@code saveAsPassenger}/{@code save} just before delegating to {@code
     * saveWithoutId}) -- it still writes the real UUID. Real symptom: summoning a bound mount always
     * failed with "Something went wrong summoning that mount," since {@code loadEntityRecursive}
     * had no {@code "id"} key to resolve an {@code EntityType} from at all. Fixed by calling {@code
     * mount.save(output)} instead (writes the type id, then delegates to {@code saveWithoutId} for
     * everything else, exactly mirroring vanilla's own passenger-save sequence), and by explicitly
     * stripping the real "UUID" key afterward -- confirmed via the same decompiled source that
     * {@code Entity#load} only overwrites its own freshly-constructed UUID if the key is present at
     * all, so a summon (first or repeated) always gets a genuinely fresh identity, matching the
     * spec's own "a clone of it" framing instead of silently reusing the original mount's UUID.
     */
    public static CompoundTag capture(Entity mount, ServerLevel level) {
        ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(mount.problemPath(), LyfeMod.LOGGER);
        try {
            TagValueOutput output = TagValueOutput.createWithContext(reporter, level.registryAccess());
            mount.save(output);
            CompoundTag tag = output.buildResult();
            tag.remove("UUID");
            return tag;
        } finally {
            reporter.close();
        }
    }

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
