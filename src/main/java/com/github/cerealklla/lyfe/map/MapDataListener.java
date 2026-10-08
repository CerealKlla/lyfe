package com.github.cerealklla.lyfe.map;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.github.cerealklla.cartographyr.api.Cartography;
import com.github.cerealklla.cartographyr.geo.EntityId;
import com.github.cerealklla.cartographyr.geo.GeographicEntity;
import com.github.cerealklla.cartographyr.geo.Layer;
import com.github.cerealklla.cartographyr.geo.LifecycleState;

import com.github.cerealklla.lyfe.knowledge.PlayerKnowledge;
import com.github.cerealklla.lyfe.registration.ModAttachments;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Resolves {@link RequestMapDataPayload} into {@link MapSettlementsPayload} -- only ever invoked
 * when Cartographyr is loaded (guarded at the call site in {@code LyfeMod#registerPayloads}, same
 * pattern as every other Cartographyr-touching handler there).
 *
 * <p>Deliberately driven by the player's own {@code knowledge.PlayerKnowledge} entries rather than a
 * live world scan (unlike {@code minimap.MinimapTracker}, which shows *nearby* entities regardless of
 * whether the player has ever been there) -- this is exactly the "only show places the player has
 * been" behavior the original Expeditionist spec asked for (point 8), for free, since
 * {@code location.LocationTracker#resolve} already calls {@code PlayerKnowledge#markVisited} for
 * every Region/Settlement entity a player physically stands on.
 */
public final class MapDataListener {

    private MapDataListener() {
    }

    public static void handleRequest(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        PlayerKnowledge knowledge = player.getData(ModAttachments.PLAYER_KNOWLEDGE);
        List<MapSettlementsPayload.Entry> entries = new ArrayList<>();

        for (long placeId : knowledge.entries().keySet()) {
            Optional<GeographicEntity> maybeEntity = Cartography.getEntity(level, new EntityId(placeId));
            if (maybeEntity.isEmpty()) {
                continue;
            }
            GeographicEntity entity = maybeEntity.get();
            if (!entity.layerId().equals(Layer.SETTLEMENT_ID) || entity.lifecycleState() != LifecycleState.REALIZED) {
                continue;
            }
            String displayText = Cartography.getDisplayText(level, entity.id()).orElse("");
            entries.add(new MapSettlementsPayload.Entry(entity.geometry().centerBlockX(), entity.geometry().centerBlockZ(), displayText));
        }

        PacketDistributor.sendToPlayer(player, new MapSettlementsPayload(entries));
    }
}
