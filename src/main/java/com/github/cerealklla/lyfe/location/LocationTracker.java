package com.github.cerealklla.lyfe.location;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.github.cerealklla.cartographyr.api.Cartography;
import com.github.cerealklla.cartographyr.geo.Classification;
import com.github.cerealklla.cartographyr.geo.EntityId;
import com.github.cerealklla.cartographyr.geo.GeographicEntity;
import com.github.cerealklla.cartographyr.geo.Layer;

import com.github.cerealklla.lyfe.LyfeMod;
import com.github.cerealklla.lyfe.geo.DisplayText;
import com.github.cerealklla.lyfe.knowledge.PlayerKnowledge;
import com.github.cerealklla.lyfe.registration.ModAttachments;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Detects the player's current geographic entities (via Cartographyr's public, read-only API),
 * one per {@code layerId}, and sends them to their client as a placement-sorted {@link
 * LocationPayload} for {@link LocationOverlay}. Only ever registered when Cartographyr is
 * actually loaded -- see {@code LyfeMod}'s soft-dependency gate.
 *
 * <p>Moved here from Cartographyr 2026-09-24 at the user's request (see decisions.md): a player's
 * on-screen location readout is player *knowledge*, not world truth, so it belongs to Lyfe even
 * though the underlying place data still comes from Cartographyr.
 *
 * <p>Reworked 2026-09-24 (second pass) to render one line per {@code Layer} instead of a single
 * arbitrary entity -- see decisions.md for the Layer registry this consumes.
 *
 * <p>Reworked again 2026-09-24 (third pass) to actually record into {@link PlayerKnowledge}
 * (design doc Section 9.3) instead of just mirroring Cartographyr's live truth -- see {@link
 * #detectCandidates} for how presence and knowledge end up being the same check here.
 */
public final class LocationTracker {

    // Five times a second; NeoForge has no built-in throttled tick event, so this is a manual
    // modulo guard inside the every-tick listener (same rate Cartographyr's original version used).
    private static final int CHECK_INTERVAL_TICKS = 4;

    // Session-only (in-memory, never persisted) -- see the class doc: this is a live mirror of
    // Cartographyr's truth, not yet real persisted player knowledge. Keyed by layerId -> the
    // chosen entity's id, so a change in any one layer (not just the whole snapshot) is detected.
    private final Map<UUID, Map<Identifier, EntityId>> lastNotifiedRegion = new HashMap<>();

    // Debounce: a candidate snapshot must be seen on two consecutive checks before it's announced,
    // so briefly clipping a jagged biome border doesn't repeatedly re-fire the update. Ported as-is
    // from Cartographyr's original implementation -- see that mod's decisions.md for why this
    // exists.
    private final Map<UUID, Map<Identifier, EntityId>> pendingRegion = new HashMap<>();

    /**
     * Proactively syncs the current location on (re)join. Without this, a player reconnecting
     * without having moved would see a blank overlay indefinitely: the tick handler only sends an
     * update when the detected snapshot *changes*, but the client's overlay state is fresh/blank on
     * every new connection regardless of whether the server-side state for them is unchanged.
     */
    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ServerLevel level = (ServerLevel) player.level();

        Map<Identifier, GeographicEntity> byLayer = detectCandidates(level, player);
        if (byLayer.isEmpty()) {
            return;
        }

        UUID playerId = player.getUUID();
        pendingRegion.remove(playerId);
        lastNotifiedRegion.put(playerId, toSnapshot(byLayer));
        sendLocation(player, byLayer);
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
        Map<Identifier, GeographicEntity> byLayer = detectCandidates(level, player);
        if (byLayer.isEmpty()) {
            return;
        }

        notifyIfChanged(player, byLayer);
    }

    /**
     * Queries what's at the player's position, grouped by {@code layerId}. Within a single layer,
     * more than one entity can legitimately be present at once (e.g. a settlement inside its
     * surrounding natural region both use the built-in {@code Layer.LOCATION_ID}) -- prefers the
     * {@link Classification#CONSTRUCTED} one over {@link Classification#NATURAL} in that case
     * (being in a town is more specific/informative than the region around it), otherwise keeps
     * whichever was seen first. A small, explicit judgment call, not an exhaustive priority system.
     *
     * <p>Every candidate found here is immediately {@link PlayerKnowledge#markVisited} -- physical
     * presence is what makes the overlay knowledge-gated rather than a raw live-truth readout
     * (design doc Section 9.3, resolving the simplification flagged since this class was first
     * written): the display only ever shows what {@code detectCandidates} returns, and everything
     * it returns was just recorded as known, so there's no separate filter step needed here --
     * the gate and the detection are the same operation by construction.
     */
    private Map<Identifier, GeographicEntity> detectCandidates(ServerLevel level, ServerPlayer player) {
        Set<GeographicEntity> here = Cartography.getEntitiesAt(level, player.getBlockX(), player.getBlockZ());
        if (here.isEmpty()) {
            here = Cartography.discoverNaturalRegion(level, player.blockPosition())
                    .map(Set::of)
                    .orElse(Set.of());
        }

        PlayerKnowledge knowledge = player.getData(ModAttachments.PLAYER_KNOWLEDGE);
        Map<Identifier, GeographicEntity> byLayer = new HashMap<>();
        for (GeographicEntity entity : here) {
            knowledge.markVisited(entity.id().value());
            byLayer.merge(entity.layerId(), entity, LocationTracker::preferWithinLayer);
        }
        return byLayer;
    }

    private static GeographicEntity preferWithinLayer(GeographicEntity a, GeographicEntity b) {
        if (a.classification().equals(Classification.CONSTRUCTED)) {
            return a;
        }
        if (b.classification().equals(Classification.CONSTRUCTED)) {
            return b;
        }
        return a;
    }

    private static Map<Identifier, EntityId> toSnapshot(Map<Identifier, GeographicEntity> byLayer) {
        Map<Identifier, EntityId> snapshot = new HashMap<>();
        byLayer.forEach((layerId, entity) -> snapshot.put(layerId, entity.id()));
        return snapshot;
    }

    private void notifyIfChanged(ServerPlayer player, Map<Identifier, GeographicEntity> byLayer) {
        UUID playerId = player.getUUID();
        Map<Identifier, EntityId> snapshot = toSnapshot(byLayer);

        Map<Identifier, EntityId> lastNotified = lastNotifiedRegion.get(playerId);
        if (snapshot.equals(lastNotified)) {
            pendingRegion.remove(playerId);
            return;
        }

        Map<Identifier, EntityId> pending = pendingRegion.get(playerId);
        if (!snapshot.equals(pending)) {
            // First sighting of this snapshot -- wait for a second consecutive match before
            // announcing it, rather than firing immediately.
            pendingRegion.put(playerId, snapshot);
            return;
        }

        // Confirmed: this snapshot was also seen on the previous check.
        pendingRegion.remove(playerId);
        lastNotifiedRegion.put(playerId, snapshot);
        sendLocation(player, byLayer);
    }

    private void sendLocation(ServerPlayer player, Map<Identifier, GeographicEntity> byLayer) {
        List<LocationPayload.LocationLine> lines = byLayer.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<Identifier, GeographicEntity> e) -> placementFor(e.getKey())).reversed())
                .map(e -> new LocationPayload.LocationLine(labelFor(e.getKey()), DisplayText.forEntity(e.getValue())))
                .toList();

        LyfeMod.LOGGER.info("Player {} location updated: {}", player.getName().getString(), lines);
        PacketDistributor.sendToPlayer(player, new LocationPayload(lines));
    }

    private static int placementFor(Identifier layerId) {
        return Cartography.getLayer(layerId).map(Layer::placement).orElse(0);
    }

    /** Falls back to a title-cased version of the layer id's path if the layer isn't registered
     * this session (e.g. the mod that owns it isn't loaded) -- so a dangling reference still shows
     * something reasonable instead of silently dropping the line. */
    private static String labelFor(Identifier layerId) {
        return Cartography.getLayer(layerId)
                .map(Layer::label)
                .orElseGet(() -> capitalize(layerId.getPath()));
    }

    private static String capitalize(String path) {
        if (path.isEmpty()) {
            return path;
        }
        return Character.toUpperCase(path.charAt(0)) + path.substring(1);
    }
}
