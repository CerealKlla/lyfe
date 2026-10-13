package com.github.cerealklla.lyfe.map;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.github.cerealklla.cartographyr.api.Cartography;
import com.github.cerealklla.cartographyr.geo.EntityId;
import com.github.cerealklla.cartographyr.geo.GeographicEntity;
import com.github.cerealklla.cartographyr.geo.Layer;
import com.github.cerealklla.cartographyr.geo.LifecycleState;

import com.github.cerealklla.lyfe.knowledge.KnowledgeEntry;
import com.github.cerealklla.lyfe.knowledge.KnowledgeFactor;
import com.github.cerealklla.lyfe.knowledge.PlayerKnowledge;
import com.github.cerealklla.lyfe.registration.ModAttachments;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Real query backing {@code api.Lyfe#getKnownSettlementsNear} -- added 2026-10-13 for Settlemynts'
 * Establish Highway quest, which needs "every settlement the Mayor has personal knowledge of
 * (including partially known ones), within some radius" -- the exact same knowledge-gated filter
 * {@code map.MapDataListener} already builds for its own full-screen Map, factored out here so both
 * consumers share one real implementation instead of silently diverging over what "known" means.
 *
 * <p>Kept in this package (already hard-dependent on Cartographyr, same as {@code MapDataListener})
 * rather than inline in {@code api.Lyfe}, so that class itself never directly references a
 * Cartographyr type -- {@code api.Lyfe#getKnownSettlementsNear} only calls in here after its own
 * {@code ModList} guard passes, same "keep the Cartographyr-touching code in a dedicated,
 * conditionally-invoked class" discipline this mod already follows everywhere else it touches that
 * optional dependency.
 */
public final class KnownSettlementsQuery {

    private KnownSettlementsQuery() {
    }

    /** {@code fullyKnown} mirrors {@code MapDataListener}'s own notion of "known" -- visited, or every {@link KnowledgeFactor} already learned; otherwise this is a partial-knowledge entry. */
    public record Result(long placeId, int x, int z, String displayText, boolean fullyKnown) {
    }

    public static List<Result> find(ServerPlayer player, ServerLevel level, BlockPos origin, double radiusBlocks) {
        PlayerKnowledge knowledge = player.getData(ModAttachments.PLAYER_KNOWLEDGE);
        List<Result> results = new ArrayList<>();
        double radiusSq = radiusBlocks * radiusBlocks;
        for (Map.Entry<Long, KnowledgeEntry> entryPair : knowledge.entries().entrySet()) {
            long placeId = entryPair.getKey();
            Optional<GeographicEntity> maybeEntity = Cartography.getEntity(level, new EntityId(placeId));
            if (maybeEntity.isEmpty()) {
                continue;
            }
            GeographicEntity entity = maybeEntity.get();
            if (!entity.layerId().equals(Layer.SETTLEMENT_ID) || entity.lifecycleState() != LifecycleState.REALIZED) {
                continue;
            }
            int x = entity.geometry().centerBlockX();
            int z = entity.geometry().centerBlockZ();
            double dx = x - origin.getX();
            double dz = z - origin.getZ();
            if (dx * dx + dz * dz > radiusSq) {
                continue;
            }
            KnowledgeEntry entry = entryPair.getValue();
            boolean fullyKnown = entry.visited() || entry.locationFactors().size() >= KnowledgeFactor.values().length;
            String displayText = Cartography.getDisplayText(level, entity.id()).orElse("");
            results.add(new Result(placeId, x, z, displayText, fullyKnown));
        }
        return results;
    }
}
