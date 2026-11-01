package com.github.cerealklla.lyfe.companion;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * A Tavern Companion Token's persisted state (Settlemynts' design-document.md Section 17, the
 * "companion summoning token, same concept to how Allays work" request) -- near-mirror of {@code
 * summoning.AllayBottleData} (a cooldown and the currently-summoned companion's own UUID if one is
 * out right now), but carries the companion's gear/stats/display name as plain structured data
 * instead of a captured {@code Entity#save} NBT blob.
 *
 * <p><b>Rebuilt 2026-10-31, same day as the first pass</b> -- the original version captured the
 * hired NPC's entire live-entity state via {@link com.github.cerealklla.lyfe.summoning.EntityCapture}
 * (mirroring {@code AllayBottleData}'s own technique), which real play immediately broke: vanilla's
 * own {@code Entity#save} is a hard no-op (writes nothing at all, not even the type id) for any
 * entity that {@code isPassenger()} -- and the NPC is always still mounted on its stool's seat at
 * the exact moment it's hired. The user's own follow-up question cut to the real issue, not just the
 * bug: nothing about this requires a live {@code Entity} to be serialized at all -- the gear
 * (already rolled by {@code TavernGearRoller} at spawn) and stats (likewise, {@code
 * TavernStatRoller}) are already known, ordinary data the instant the NPC exists; a vanilla Allay/
 * Summoning Stone mount genuinely needs the generic whole-entity-NBT approach (arbitrary, unbounded
 * vanilla state), but a Tavern Companion's state is exactly these few fields. Minting now reads the
 * live NPC's current equipment/attributes directly (no save/serialize round-trip, so the
 * mounted-passenger case was never actually a problem to begin with) and {@link #entityTypeId}
 * (resolved once via the registry, e.g. {@code settlemynts:tavern_npc}) is all Lyfe needs to spawn
 * the right kind of entity back -- no compile-time dependency on Settlemynts' own entity class, same
 * decoupling principle the old NBT-type-id approach already relied on.
 */
public record CompanionTokenData(Identifier entityTypeId, List<EquipmentEntry> equipment, double maxHealth,
                                  double attackDamage, long cooldownEndEpochMillis,
                                  Optional<UUID> summonedCompanionId, String displayName, ItemStack mountItem) {

    public record EquipmentEntry(EquipmentSlot slot, ItemStack stack) {
        public static final Codec<EquipmentEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
                EquipmentSlot.CODEC.fieldOf("slot").forGetter(EquipmentEntry::slot),
                ItemStack.CODEC.fieldOf("stack").forGetter(EquipmentEntry::stack)
        ).apply(i, EquipmentEntry::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, EquipmentEntry> STREAM_CODEC = StreamCodec.composite(
                EquipmentSlot.STREAM_CODEC, EquipmentEntry::slot,
                ItemStack.STREAM_CODEC, EquipmentEntry::stack,
                EquipmentEntry::new);
    }

    public static final Codec<CompanionTokenData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("entity_type_id").forGetter(CompanionTokenData::entityTypeId),
            EquipmentEntry.CODEC.listOf().fieldOf("equipment").forGetter(CompanionTokenData::equipment),
            Codec.DOUBLE.fieldOf("max_health").forGetter(CompanionTokenData::maxHealth),
            Codec.DOUBLE.fieldOf("attack_damage").forGetter(CompanionTokenData::attackDamage),
            Codec.LONG.optionalFieldOf("cooldown_end_epoch_millis", 0L).forGetter(CompanionTokenData::cooldownEndEpochMillis),
            UUIDUtil.CODEC.optionalFieldOf("summoned_companion_id").forGetter(CompanionTokenData::summonedCompanionId),
            Codec.STRING.optionalFieldOf("display_name", "Companion").forGetter(CompanionTokenData::displayName),
            ItemStack.OPTIONAL_CODEC.optionalFieldOf("mount_item", ItemStack.EMPTY).forGetter(CompanionTokenData::mountItem)
    ).apply(i, CompanionTokenData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, CompanionTokenData> STREAM_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, CompanionTokenData::entityTypeId,
            ByteBufCodecs.collection(java.util.ArrayList::new, EquipmentEntry.STREAM_CODEC), CompanionTokenData::equipment,
            ByteBufCodecs.DOUBLE, CompanionTokenData::maxHealth,
            ByteBufCodecs.DOUBLE, CompanionTokenData::attackDamage,
            ByteBufCodecs.VAR_LONG, CompanionTokenData::cooldownEndEpochMillis,
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), CompanionTokenData::summonedCompanionId,
            ByteBufCodecs.STRING_UTF8, CompanionTokenData::displayName,
            ItemStack.STREAM_CODEC, CompanionTokenData::mountItem,
            CompanionTokenData::new);

    public boolean onCooldown(long currentEpochMillis) {
        return currentEpochMillis < cooldownEndEpochMillis;
    }

    public CompanionTokenData withEquipmentAndStats(List<EquipmentEntry> newEquipment, double newMaxHealth, double newAttackDamage) {
        return new CompanionTokenData(entityTypeId, newEquipment, newMaxHealth, newAttackDamage,
                cooldownEndEpochMillis, summonedCompanionId, displayName, mountItem);
    }

    /** Player-edited gear only (the Equip GUI, see {@code companion.CompanionTokenMenu}) -- stats are untouched. */
    public CompanionTokenData withEquipment(List<EquipmentEntry> newEquipment) {
        return new CompanionTokenData(entityTypeId, newEquipment, maxHealth, attackDamage,
                cooldownEndEpochMillis, summonedCompanionId, displayName, mountItem);
    }

    public CompanionTokenData withSummonedCompanionId(Optional<UUID> id) {
        return new CompanionTokenData(entityTypeId, equipment, maxHealth, attackDamage, cooldownEndEpochMillis, id, displayName, mountItem);
    }

    public CompanionTokenData withCooldownEndEpochMillis(long end) {
        return new CompanionTokenData(entityTypeId, equipment, maxHealth, attackDamage, end, summonedCompanionId, displayName, mountItem);
    }

    /** "Rename" on {@code client.CompanionTokenScreen}, real user request, 2026-11-01. */
    public CompanionTokenData withDisplayName(String newDisplayName) {
        return new CompanionTokenData(entityTypeId, equipment, maxHealth, attackDamage,
                cooldownEndEpochMillis, summonedCompanionId, newDisplayName, mountItem);
    }

    /** The Equip GUI's own new "Mount" slot, real user request, 2026-11-01: "add 1 more slot on the companion where you are allowed to equip a summon stone." Holds a real Summoning Stone {@code ItemStack} (with its own embedded state), not an {@link EquipmentEntry} -- a mount isn't vanilla equipment. */
    public CompanionTokenData withMountItem(ItemStack newMountItem) {
        return new CompanionTokenData(entityTypeId, equipment, maxHealth, attackDamage,
                cooldownEndEpochMillis, summonedCompanionId, displayName, newMountItem);
    }
}
