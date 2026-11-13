package com.github.cerealklla.lyfe.minimap;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Sends which real saved piece the player is currently standing inside ({@link
 * WyrldPieceSyncPayload}), while they're in TinyWyrlds' Wyrld Space dimension -- a sibling to
 * {@link MinimapTracker}, same throttled-tick/dedupe-by-last-sent shape, only ever registered
 * when TinyWyrlds is loaded (see {@code LyfeMod}). Only sends a fresh payload when the resolved
 * piece actually changes, not on every check -- {@link MinimapOverlay} only needs the latest
 * value, it doesn't need to be told "nothing changed" over and over.
 */
public final class WyrldPieceTracker {

    private static final int CHECK_INTERVAL_TICKS = 5;

    /** {@code null} once sent means "known to be absent" -- distinct from "never checked yet", so a
     *  player who leaves a Wyrld entirely still gets exactly one "not present" payload instead of
     *  silently keeping their last real piece's bounds forever. */
    private final Map<UUID, Optional<TinyWyrldsMinimapBridge.PieceBounds>> lastSent = new ConcurrentHashMap<>();

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.tickCount % CHECK_INTERVAL_TICKS != 0) {
            return;
        }

        Optional<TinyWyrldsMinimapBridge.PieceBounds> current = TinyWyrldsMinimapBridge.getCurrentPiece(player);
        Optional<TinyWyrldsMinimapBridge.PieceBounds> last = lastSent.get(player.getUUID());
        if (current.equals(last)) {
            return;
        }
        lastSent.put(player.getUUID(), current);

        if (current.isPresent()) {
            TinyWyrldsMinimapBridge.PieceBounds bounds = current.get();
            PacketDistributor.sendToPlayer(player, new WyrldPieceSyncPayload(true, bounds.min(), bounds.maxExclusive()));
        } else {
            PacketDistributor.sendToPlayer(player, new WyrldPieceSyncPayload(false, BlockPos.ZERO, BlockPos.ZERO));
        }
    }
}
