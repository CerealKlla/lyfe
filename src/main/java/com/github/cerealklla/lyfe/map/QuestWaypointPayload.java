package com.github.cerealklla.lyfe.map;

import java.util.Optional;

import io.netty.buffer.ByteBuf;

import com.github.cerealklla.lyfe.LyfeMod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server-to-client: sets or clears (empty {@code pos}) the local player's quest-origin waypoint --
 * see {@code map.ClientQuestWaypointState}'s own doc. Always targets the receiving player only (a
 * quest hint is private), sent via {@code api.Lyfe#setQuestWaypoint}/{@code #clearQuestWaypoint}.
 * {@code dimensionId} is the dimension's own registry key as a plain {@link Identifier} (e.g.
 * {@code minecraft:overworld}), not a full {@code ResourceKey} -- simpler to stream, reconstructed
 * client-side via {@code ResourceKey.create(Registries.DIMENSION, dimensionId)} only where needed.
 */
public record QuestWaypointPayload(Optional<BlockPos> pos, Identifier dimensionId, double radiusBlocks) implements CustomPacketPayload {

    public static final Type<QuestWaypointPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(LyfeMod.MODID, "quest_waypoint"));

    public static final StreamCodec<ByteBuf, QuestWaypointPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(BlockPos.STREAM_CODEC), QuestWaypointPayload::pos,
            ByteBufCodecs.fromCodec(Identifier.CODEC), QuestWaypointPayload::dimensionId,
            ByteBufCodecs.DOUBLE, QuestWaypointPayload::radiusBlocks,
            QuestWaypointPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
