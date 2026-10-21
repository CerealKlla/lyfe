package com.github.cerealklla.lyfe.magician;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

/**
 * Persisted per-{@code ItemStack} roll result for a Focus (design doc Section 25: "their stats
 * will be randomized at time of crafting") -- mirrors {@code socket.SocketData}'s shape exactly.
 * Rolled once in {@code CraftingStructureMenu#attemptCraft} via {@link FocusStatRoller} and never
 * changed afterward.
 */
public record FocusData(FocusVariant variant, List<RolledStat> stats, Optional<Identifier> grantedSpellId) {

    public record RolledStat(FocusStatType type, double value) {
        public static final Codec<RolledStat> CODEC = RecordCodecBuilder.create(i -> i.group(
                FocusStatType.CODEC.fieldOf("type").forGetter(RolledStat::type),
                Codec.DOUBLE.fieldOf("value").forGetter(RolledStat::value)
        ).apply(i, RolledStat::new));
    }

    public FocusData {
        stats = List.copyOf(stats);
    }

    public static final Codec<FocusData> CODEC = RecordCodecBuilder.create(i -> i.group(
            FocusVariant.CODEC.fieldOf("variant").forGetter(FocusData::variant),
            RolledStat.CODEC.listOf().fieldOf("stats").forGetter(FocusData::stats),
            Identifier.CODEC.optionalFieldOf("granted_spell_id").forGetter(FocusData::grantedSpellId)
    ).apply(i, FocusData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, FocusData> STREAM_CODEC =
            ByteBufCodecs.fromCodecWithRegistries(CODEC);
}
