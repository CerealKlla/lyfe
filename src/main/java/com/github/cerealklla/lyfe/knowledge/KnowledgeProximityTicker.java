package com.github.cerealklla.lyfe.knowledge;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.github.cerealklla.lyfe.registration.ModAttachments;
import com.github.cerealklla.lyfe.registration.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Proximity-based knowledge reading for Cartographyr signs/maps-in-frames (design doc Section 9.1,
 * reworked 2026-10-05 -- explicit user request: "Direction Signs/Maps on Walls need to grant
 * knowledge when a player walks within 4 blocks of them, not on right click"). {@link
 * SignListener#onRightClickBlock}/{@link SignListener#onEntityInteract} no longer call {@code
 * handleRead} at all -- this class is now the only place either actually fires.
 *
 * <p>Held maps (read via a direct right-click in hand, {@link SignListener#onRightClickItem}) are
 * untouched -- they're not "on a wall," and the user's request was specifically about signs/frames.
 *
 * <p>Runs once every {@link #CHECK_INTERVAL_TICKS} per player, scanning a {@value
 * #RADIUS_BLOCKS}-block-radius sphere of block positions (for bound signs) and a same-radius entity
 * AABB (for bound item-frame maps). A per-player "currently in range" set (keyed by block position
 * for signs, entity id for frames) makes each physical sign/map fire {@link
 * SignListener#handleProximityRead} exactly once per approach, not once per scan while standing
 * still nearby -- re-entering range after leaving fires it again, which is harmless since {@code
 * PlayerKnowledge#learnLocationFactors} is a pure, idempotent set-merge and a no-op re-read is
 * silently skipped (no XP, no chat spam -- see {@code handleProximityRead}'s own doc).
 */
public final class KnowledgeProximityTicker {

    private static final int CHECK_INTERVAL_TICKS = 10; // 2x/sec -- frequent enough that walking past at normal speed won't skip it.
    private static final int RADIUS_BLOCKS = 4;

    private final SignListener signListener = new SignListener();
    private final Map<UUID, Set<BlockPos>> nearbySigns = new HashMap<>();
    private final Map<UUID, Set<Integer>> nearbyFrames = new HashMap<>();

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.tickCount % CHECK_INTERVAL_TICKS != 0) {
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        UUID playerId = player.getUUID();
        BlockPos center = player.blockPosition();

        Set<BlockPos> currentSigns = new HashSet<>();
        Set<BlockPos> previousSigns = nearbySigns.getOrDefault(playerId, Set.of());
        double radiusSq = (double) RADIUS_BLOCKS * RADIUS_BLOCKS;
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-RADIUS_BLOCKS, -RADIUS_BLOCKS, -RADIUS_BLOCKS),
                center.offset(RADIUS_BLOCKS, RADIUS_BLOCKS, RADIUS_BLOCKS))) {
            if (pos.distSqr(center) > radiusSq) {
                continue;
            }
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (!(blockEntity instanceof SignBlockEntity)) {
                continue;
            }
            BlockPos immutable = pos.immutable();
            currentSigns.add(immutable);
            if (!previousSigns.contains(immutable)) {
                blockEntity.getExistingData(ModAttachments.SIGN_REFERENCE)
                        .ifPresent(reference -> signListener.handleProximityRead(player, reference));
            }
        }
        nearbySigns.put(playerId, currentSigns);

        Set<Integer> currentFrames = new HashSet<>();
        Set<Integer> previousFrames = nearbyFrames.getOrDefault(playerId, Set.of());
        for (ItemFrame frame : level.getEntitiesOfClass(ItemFrame.class, player.getBoundingBox().inflate(RADIUS_BLOCKS))) {
            KnowledgeReference reference = frame.getItem().get(ModItems.KNOWLEDGE_REFERENCE);
            if (reference == null) {
                continue;
            }
            int frameId = frame.getId();
            currentFrames.add(frameId);
            if (!previousFrames.contains(frameId)) {
                signListener.handleProximityRead(player, reference);
            }
        }
        nearbyFrames.put(playerId, currentFrames);
    }
}
