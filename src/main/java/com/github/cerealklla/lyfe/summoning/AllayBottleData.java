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
import net.minecraft.world.item.ItemStack;

/**
 * An Allay in a Bottle's persisted state (design doc Section 27) -- near-exact mirror of {@link
 * SummoningStoneData} (captured snapshot, wall-clock cooldown, the currently-summoned Allay's own
 * UUID if one is out right now), plus the two slots the Equip/Gather GUI manages. All state for
 * this feature lives entirely here, on the item itself -- no separate server-side registry keyed by
 * player or by a fixed "owner" -- confirmed by explicit design: ownership is purely possession-
 * based, so trading the physical bottle to another player hands over everything it does, with zero
 * additional code needed to make that transfer "take."
 */
public record AllayBottleData(CompoundTag savedState, long cooldownEndEpochMillis, Optional<UUID> summonedAllayId,
                               ItemStack equipItem, ItemStack gatherItem, String displayName) {

    public static final Codec<AllayBottleData> CODEC = RecordCodecBuilder.create(i -> i.group(
            CompoundTag.CODEC.fieldOf("saved_state").forGetter(AllayBottleData::savedState),
            Codec.LONG.optionalFieldOf("cooldown_end_epoch_millis", 0L).forGetter(AllayBottleData::cooldownEndEpochMillis),
            UUIDUtil.CODEC.optionalFieldOf("summoned_allay_id").forGetter(AllayBottleData::summonedAllayId),
            ItemStack.OPTIONAL_CODEC.optionalFieldOf("equip_item", ItemStack.EMPTY).forGetter(AllayBottleData::equipItem),
            ItemStack.OPTIONAL_CODEC.optionalFieldOf("gather_item", ItemStack.EMPTY).forGetter(AllayBottleData::gatherItem),
            Codec.STRING.optionalFieldOf("display_name", "Allay").forGetter(AllayBottleData::displayName)
    ).apply(i, AllayBottleData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, AllayBottleData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.COMPOUND_TAG, AllayBottleData::savedState,
            ByteBufCodecs.VAR_LONG, AllayBottleData::cooldownEndEpochMillis,
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), AllayBottleData::summonedAllayId,
            ItemStack.OPTIONAL_STREAM_CODEC, AllayBottleData::equipItem,
            ItemStack.OPTIONAL_STREAM_CODEC, AllayBottleData::gatherItem,
            ByteBufCodecs.STRING_UTF8, AllayBottleData::displayName,
            AllayBottleData::new);

    public boolean onCooldown(long currentEpochMillis) {
        return currentEpochMillis < cooldownEndEpochMillis;
    }

    public AllayBottleData withSavedState(CompoundTag newState) {
        return new AllayBottleData(newState, cooldownEndEpochMillis, summonedAllayId, equipItem, gatherItem, displayName);
    }

    public AllayBottleData withSummonedAllayId(Optional<UUID> id) {
        return new AllayBottleData(savedState, cooldownEndEpochMillis, id, equipItem, gatherItem, displayName);
    }

    public AllayBottleData withCooldownEndEpochMillis(long end) {
        return new AllayBottleData(savedState, end, summonedAllayId, equipItem, gatherItem, displayName);
    }

    public AllayBottleData withEquipItem(ItemStack newEquipItem) {
        return new AllayBottleData(savedState, cooldownEndEpochMillis, summonedAllayId, newEquipItem.copy(), gatherItem, displayName);
    }

    public AllayBottleData withGatherItem(ItemStack newGatherItem) {
        return new AllayBottleData(savedState, cooldownEndEpochMillis, summonedAllayId, equipItem, newGatherItem.copy(), displayName);
    }

    public AllayBottleData withDisplayName(String newName) {
        return new AllayBottleData(savedState, cooldownEndEpochMillis, summonedAllayId, equipItem, gatherItem, newName);
    }

    /** Fresh, unbound slots -- used when minting a duplicate bottle (explicit anti-exploit requirement: a clone never copies held items). */
    public AllayBottleData withEmptySlots() {
        return new AllayBottleData(savedState, cooldownEndEpochMillis, summonedAllayId, ItemStack.EMPTY, ItemStack.EMPTY, displayName);
    }
}
