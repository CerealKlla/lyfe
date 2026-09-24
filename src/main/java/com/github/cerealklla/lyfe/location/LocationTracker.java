package com.github.cerealklla.lyfe.location;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.github.cerealklla.cartographyr.api.Cartography;
import com.github.cerealklla.cartographyr.geo.EntityId;
import com.github.cerealklla.cartographyr.geo.GeographicEntity;

import com.github.cerealklla.lyfe.LyfeMod;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Detects the player's current geographic entity (via Cartographyr's public, read-only API) and
 * sends it to their client as a {@link LocationPayload} for {@link LocationOverlay}. Only ever
 * registered when Cartographyr is actually loaded -- see {@code LyfeMod}'s soft-dependency gate.
 *
 * <p>Moved here from Cartographyr 2026-09-24 at the user's request (see decisions.md): a player's
 * on-screen location readout is player *knowledge*, not world truth, so it belongs to Lyfe even
 * though the underlying place data still comes from Cartographyr. This first cut still just
 * mirrors whatever Cartographyr reports live, though -- it isn't yet gated by, or recorded into, a
 * real persisted player-knowledge store (design doc Section 9.3), which is the natural next step
 * here (see the "noted for later" decisions.md entry on the Explorer skill/minimap idea).
 */
public final class LocationTracker {

    // Five times a second; NeoForge has no built-in throttled tick event, so this is a manual
    // modulo guard inside the every-tick listener (same rate Cartographyr's original version used).
    private static final int CHECK_INTERVAL_TICKS = 4;

    // Session-only (in-memory, never persisted) -- see the class doc: this is a live mirror of
    // Cartographyr's truth, not yet real persisted player knowledge.
    private final Map<UUID, EntityId> lastNotifiedRegion = new HashMap<>();

    // Debounce: a candidate must be seen on two consecutive checks before it's announced, so
    // briefly clipping a jagged biome border doesn't repeatedly re-fire the update. Ported as-is
    // from Cartographyr's original implementation -- see that mod's decisions.md for why this
    // exists.
    private final Map<UUID, EntityId> pendingRegion = new HashMap<>();

    /**
     * Proactively syncs the current location on (re)join. Without this, a player reconnecting
     * without having moved would see a blank overlay indefinitely: the tick handler only sends an
     * update when the detected region *changes*, but the client's overlay state is fresh/blank on
     * every new connection regardless of whether the server-side state for them is unchanged.
     */
    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        int x = player.getBlockX();
        int z = player.getBlockZ();

        Set<GeographicEntity> here = Cartography.getEntitiesAt(level, x, z);
        GeographicEntity entity = here.isEmpty()
                ? Cartography.discoverNaturalRegion(level, player.blockPosition()).orElse(null)
                : here.iterator().next();
        if (entity == null) {
            return;
        }

        UUID playerId = player.getUUID();
        pendingRegion.remove(playerId);
        lastNotifiedRegion.put(playerId, entity.id());
        sendLocation(player, entity);
    }

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        // instanceof ServerPlayer alone is sufficient to restrict this to the server side: the
        // client-side tick uses LocalPlayer, never ServerPlayer, so no separate isClientSide()
        // check is needed.
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.tickCount % CHECK_INTERVAL_TICKS != 0) {
            return;
        }

        ServerLevel level = (ServerLevel) player.level();
        int x = player.getBlockX();
        int z = player.getBlockZ();

        Set<GeographicEntity> here = Cartography.getEntitiesAt(level, x, z);
        if (!here.isEmpty()) {
            notifyIfChanged(player, here.iterator().next());
            return;
        }

        Cartography.discoverNaturalRegion(level, player.blockPosition())
                .ifPresent(entity -> notifyIfChanged(player, entity));
    }

    private void notifyIfChanged(ServerPlayer player, GeographicEntity entity) {
        UUID playerId = player.getUUID();
        EntityId lastNotified = lastNotifiedRegion.get(playerId);
        if (entity.id().equals(lastNotified)) {
            pendingRegion.remove(playerId);
            return;
        }

        EntityId pending = pendingRegion.get(playerId);
        if (!entity.id().equals(pending)) {
            // First sighting of this candidate -- wait for a second consecutive match before
            // announcing it, rather than firing immediately.
            pendingRegion.put(playerId, entity.id());
            return;
        }

        // Confirmed: this candidate was also seen on the previous check.
        pendingRegion.remove(playerId);
        lastNotifiedRegion.put(playerId, entity.id());
        sendLocation(player, entity);
    }

    private void sendLocation(ServerPlayer player, GeographicEntity entity) {
        String name = entity.name().orElse("an unnamed place");
        LyfeMod.LOGGER.info("Player {} entered {} ('{}')", player.getName().getString(), entity.id(), name);
        PacketDistributor.sendToPlayer(player, new LocationPayload(name));
    }
}
