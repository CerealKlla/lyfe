package com.github.cerealklla.lyfe.companion;

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
 * A Tavern Companion Token's persisted state (Settlemynts' design-document.md Section 17, the
 * "companion summoning token, same concept to how Allays work" request) -- near-mirror of {@code
 * summoning.AllayBottleData} (captured snapshot, wall-clock cooldown, the currently-summoned
 * companion's own UUID if one is out right now), but deliberately *without* the equip/gather item
 * pair Allay in a Bottle carries. That pair only exists because a vanilla Allay has no real
 * equipment slots of its own to serialize; a hired Tavern NPC (a {@code
 * com.github.cerealklla.settlemynts.tavern.TavernNpcEntity}, any ordinary {@code LivingEntity} with
 * real armor/offhand/mainhand slots) already carries its full gear as part of {@code savedState}'s
 * own equipment NBT, captured for free by {@link com.github.cerealklla.lyfe.summoning.EntityCapture}
 * -- no separate field needed to satisfy "retained if the NPC dies and given back when it respawns."
 */
public record CompanionTokenData(CompoundTag savedState, long cooldownEndEpochMillis,
                                  Optional<UUID> summonedCompanionId, String displayName) {

    public static final Codec<CompanionTokenData> CODEC = RecordCodecBuilder.create(i -> i.group(
            CompoundTag.CODEC.fieldOf("saved_state").forGetter(CompanionTokenData::savedState),
            Codec.LONG.optionalFieldOf("cooldown_end_epoch_millis", 0L).forGetter(CompanionTokenData::cooldownEndEpochMillis),
            UUIDUtil.CODEC.optionalFieldOf("summoned_companion_id").forGetter(CompanionTokenData::summonedCompanionId),
            Codec.STRING.optionalFieldOf("display_name", "Companion").forGetter(CompanionTokenData::displayName)
    ).apply(i, CompanionTokenData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, CompanionTokenData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.COMPOUND_TAG, CompanionTokenData::savedState,
            ByteBufCodecs.VAR_LONG, CompanionTokenData::cooldownEndEpochMillis,
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), CompanionTokenData::summonedCompanionId,
            ByteBufCodecs.STRING_UTF8, CompanionTokenData::displayName,
            CompanionTokenData::new);

    public boolean onCooldown(long currentEpochMillis) {
        return currentEpochMillis < cooldownEndEpochMillis;
    }

    public CompanionTokenData withSavedState(CompoundTag newState) {
        return new CompanionTokenData(newState, cooldownEndEpochMillis, summonedCompanionId, displayName);
    }

    public CompanionTokenData withSummonedCompanionId(Optional<UUID> id) {
        return new CompanionTokenData(savedState, cooldownEndEpochMillis, id, displayName);
    }

    public CompanionTokenData withCooldownEndEpochMillis(long end) {
        return new CompanionTokenData(savedState, end, summonedCompanionId, displayName);
    }

    public CompanionTokenData withDisplayName(String newName) {
        return new CompanionTokenData(savedState, cooldownEndEpochMillis, summonedCompanionId, newName);
    }
}
