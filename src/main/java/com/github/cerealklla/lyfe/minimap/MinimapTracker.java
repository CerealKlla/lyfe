package com.github.cerealklla.lyfe.minimap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.github.cerealklla.cartographyr.api.Cartography;
import com.github.cerealklla.cartographyr.geo.Classification;
import com.github.cerealklla.cartographyr.geo.EntityType;
import com.github.cerealklla.cartographyr.geo.GeographicEntity;
import com.github.cerealklla.cartographyr.geo.Geometry;
import com.github.cerealklla.cartographyr.geo.LifecycleState;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Sends nearby Settlemynts plot/settlement outlines for the minimap ({@link
 * MinimapEntitiesPayload}) -- a small sibling to {@code location.LocationTracker}, sharing its
 * soft-dependency shape (only ever registered when Cartographyr is loaded, see {@code LyfeMod}) and
 * its "match Settlemynts by a hardcoded Identifier, not a compiled dependency" contract (Lyfe has no
 * build-time dependency on Settlemynts).
 *
 * <p>No new spatial-index API was added to Cartographyr for this -- reuses the existing {@code
 * Cartography.findEntities(level, Classification.CONSTRUCTED)} whole-dimension scan and filters to
 * a bounding box around the player here, the same "Lyfe treats Cartographyr as read-only" boundary
 * already used everywhere else in this mod. Fine at this suite's scale.
 */
public final class MinimapTracker {

    // Lowered from 10/8, user request 2026-09-29 ("increase the refresh rate of the minimap") --
    // kept in step with MinimapOverlay's own resample throttle above so outlines don't lag noticeably
    // behind the terrain texture itself.
    private static final int CHECK_INTERVAL_TICKS = 5;
    private static final int MOVE_THRESHOLD_BLOCKS = 4;
    private static final int QUERY_RADIUS_BLOCKS = MinimapZoom.MAX_RADIUS_BLOCKS;

    private static final Identifier SETTLEMENTS_ZONE_LAYER_ID = Identifier.fromNamespaceAndPath("settlemynts", "zone");
    private static final EntityType SETTLEMENT_CORE_TYPE = new EntityType(Identifier.fromNamespaceAndPath("settlemynts", "settlement_core"));
    private static final EntityType PLOT_TYPE = new EntityType(Identifier.fromNamespaceAndPath("settlemynts", "plot"));
    // Re-added to the minimap 2026-10-08 (explicit user request, as a debugging aid for natural-
    // village plot detection/zone inference) -- was briefly excluded earlier the same day.
    private static final EntityType NATURAL_PLOT_TYPE = new EntityType(Identifier.fromNamespaceAndPath("settlemynts", "natural_plot"));
    // Cartographyr's own naturally-discovered village footprint -- a real compile dependency already
    // exists on Cartographyr here (unlike Settlemynts' types above), so this one is referenced directly.
    private static final EntityType NATURAL_SETTLEMENT_TYPE = EntityType.SETTLEMENT;

    private final Map<UUID, BlockPos> lastSentAt = new ConcurrentHashMap<>();

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.tickCount % CHECK_INTERVAL_TICKS != 0) {
            return;
        }

        BlockPos pos = player.blockPosition();
        BlockPos last = lastSentAt.get(player.getUUID());
        if (last != null && last.distManhattan(pos) < MOVE_THRESHOLD_BLOCKS) {
            return;
        }
        lastSentAt.put(player.getUUID(), pos);

        ServerLevel level = (ServerLevel) player.level();
        List<MinimapEntitiesPayload.Outline> outlines = new ArrayList<>();
        for (GeographicEntity entity : Cartography.findEntities(level, Classification.CONSTRUCTED)) {
            if (entity.lifecycleState() != LifecycleState.REALIZED || !(entity.geometry() instanceof Geometry.Polygon polygon)) {
                continue;
            }
            boolean isSettlementCore = entity.type().equals(SETTLEMENT_CORE_TYPE);
            boolean isPlot = entity.layerId().equals(SETTLEMENTS_ZONE_LAYER_ID) && entity.type().equals(PLOT_TYPE);
            boolean isNaturalPlot = entity.layerId().equals(SETTLEMENTS_ZONE_LAYER_ID) && entity.type().equals(NATURAL_PLOT_TYPE);
            boolean isNaturalSettlement = entity.type().equals(NATURAL_SETTLEMENT_TYPE);
            if (!isSettlementCore && !isPlot && !isNaturalPlot && !isNaturalSettlement) {
                continue; // Skip padded settlement buffers/plot buffers -- clutter, not a real boundary a player cares about seeing.
            }
            MinimapEntitiesPayload.Kind kind = isSettlementCore
                    ? MinimapEntitiesPayload.Kind.SETTLEMENT_CORE
                    : isPlot
                            ? MinimapEntitiesPayload.Kind.PLOT
                            : isNaturalPlot
                                    ? MinimapEntitiesPayload.Kind.NATURAL_PLOT
                                    : MinimapEntitiesPayload.Kind.NATURAL_SETTLEMENT;

            List<Integer> relX = new ArrayList<>(polygon.vertices().size());
            List<Integer> relZ = new ArrayList<>(polygon.vertices().size());
            boolean withinRange = false;
            for (Geometry.Polygon.Vertex vertex : polygon.vertices()) {
                int dx = vertex.x() - pos.getX();
                int dz = vertex.z() - pos.getZ();
                if (Math.abs(dx) <= QUERY_RADIUS_BLOCKS && Math.abs(dz) <= QUERY_RADIUS_BLOCKS) {
                    withinRange = true;
                }
                relX.add(dx);
                relZ.add(dz);
            }
            if (withinRange) {
                outlines.add(new MinimapEntitiesPayload.Outline(kind, relX, relZ));
            }
        }

        if (outlines.stream().anyMatch(o -> o.kind() == MinimapEntitiesPayload.Kind.NATURAL_PLOT) || outlines.isEmpty()) {
            long naturalPlotCount = outlines.stream().filter(o -> o.kind() == MinimapEntitiesPayload.Kind.NATURAL_PLOT).count();
            com.github.cerealklla.lyfe.LyfeMod.LOGGER.info(
                    "MinimapTracker: sending {} outline(s) to {} ({} NATURAL_PLOT)", outlines.size(), player.getName().getString(), naturalPlotCount);
        }
        PacketDistributor.sendToPlayer(player, new MinimapEntitiesPayload(outlines));
    }
}
