package com.github.cerealklla.lyfe.location;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.github.cerealklla.cartographyr.api.Cartography;
import com.github.cerealklla.cartographyr.geo.DisplayText;
import com.github.cerealklla.cartographyr.geo.EntityType;
import com.github.cerealklla.cartographyr.geo.GeographicEntity;
import com.github.cerealklla.cartographyr.geo.Layer;
import com.github.cerealklla.cartographyr.geo.LifecycleState;

import com.github.cerealklla.lyfe.LyfeMod;
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
 * Detects the player's current geographic entities (via Cartographyr's public, read-only API) and
 * resolves them into a fixed two-line HUD for {@link LocationOverlay} -- design doc Section
 * 9.3-adjacent, reworked 2026-09-26 (explicit user request) from an earlier generic "one line per
 * Cartographyr Layer" model. Only ever registered when Cartographyr is actually loaded -- see
 * {@code LyfeMod}'s soft-dependency gate.
 *
 * <p>Moved here from Cartographyr 2026-09-24 at the user's request (see decisions.md): a player's
 * on-screen location readout is player *knowledge*, not world truth, so it belongs to Lyfe even
 * though the underlying place data still comes from Cartographyr.
 *
 * <p><b>Settlemynts entity types/layers are matched by a hardcoded {@code Identifier}, not a
 * compiled dependency</b> -- Lyfe has no build-time dependency on Settlemynts (unlike Cartographyr/
 * Yconomics), so "does this entity come from Settlemynts' plot mechanic" is necessarily a soft,
 * string-based cross-mod contract, the same way {@code Layer} ids are already shared this way.
 * If Settlemynts ever renames these ids, this resolution silently stops recognizing plots/
 * settlement-core entities rather than failing loudly -- an accepted risk of not compiling against
 * it, flagged here rather than silently assumed.
 */
public final class LocationTracker {

    // Five times a second; NeoForge has no built-in throttled tick event, so this is a manual
    // modulo guard inside the every-tick listener (same rate Cartographyr's original version used).
    private static final int CHECK_INTERVAL_TICKS = 4;

    private static final Identifier SETTLEMENTS_ZONE_LAYER_ID = Identifier.fromNamespaceAndPath("settlemynts", "zone");
    private static final EntityType SETTLEMENT_CORE_TYPE = new EntityType(Identifier.fromNamespaceAndPath("settlemynts", "settlement_core"));
    private static final EntityType PLOT_TYPE = new EntityType(Identifier.fromNamespaceAndPath("settlemynts", "plot"));
    private static final EntityType PLOT_BUFFER_TYPE = new EntityType(Identifier.fromNamespaceAndPath("settlemynts", "plot_buffer"));

    // Matched by exact label text, not an id -- Cartographyr's designation field only ever stores
    // Settlemynts' ZoneType#label() string (see FinalizePlotPayload's handler), not its Identifier.
    // Same soft cross-mod contract as the class doc's own note.
    private static final String PRIVATE_RESIDENCE_LABEL = "Private Residence";

    /** What the HUD should show right now -- also doubles as the debounce/change-detection key (see {@link #notifyIfChanged}), so two genuinely different places that happen to render identical text are treated as "no change." An accepted simplification, not expected to matter in practice. */
    private record LocationSnapshot(Optional<String> line1, Optional<String> line2) {
        static final LocationSnapshot EMPTY = new LocationSnapshot(Optional.empty(), Optional.empty());
    }

    // Session-only (in-memory, never persisted) -- a live mirror of Cartographyr's truth, not yet
    // real persisted player knowledge (see PlayerKnowledge for the part that is).
    private final Map<UUID, LocationSnapshot> lastNotified = new ConcurrentHashMap<>();

    // Debounce: a candidate snapshot must be seen on two consecutive checks before it's announced,
    // so briefly clipping a jagged biome border doesn't repeatedly re-fire the update. Ported as-is
    // from Cartographyr's original implementation -- see that mod's decisions.md for why this
    // exists.
    private final Map<UUID, LocationSnapshot> pending = new ConcurrentHashMap<>();

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

        LocationSnapshot snapshot = resolve(level, player);

        UUID playerId = player.getUUID();
        pending.remove(playerId);
        lastNotified.put(playerId, snapshot);
        sendLocation(player, snapshot);
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
        LocationSnapshot snapshot = resolve(level, player);
        notifyIfChanged(player, snapshot);
    }

    /**
     * Queries what's at the player's position and resolves it into the two HUD lines. Every
     * candidate found is immediately {@link PlayerKnowledge#markVisited} -- physical presence is
     * what makes the overlay knowledge-gated rather than a raw live-truth readout (design doc
     * Section 9.3): the display only ever shows what's resolved here, and everything found was
     * just recorded as known, so there's no separate filter step needed.
     *
     * <p><b>Exception, added 2026-10-02</b> (explicit user decision, see decisions.md): Settlemynts
     * plot/plot-buffer entities are deliberately excluded from this. A settlement with N plots would
     * otherwise write N near-identical, essentially never-read entries per player into {@code
     * PlayerKnowledge} just from walking through -- the user's own reasoning was that per-plot
     * directions are a Mayor/Town-Planner signage concern, which should read Cartographyr's
     * authoritative data directly rather than routing through a player's personal discovery log.
     * These entities are still resolved into {@code plot}/{@code plotBuffer} below for the HUD's own
     * "Residence"/"Town Proper" text -- that display doesn't depend on {@code PlayerKnowledge} at
     * all, only the sign/map knowledge grant does.
     *
     * <p><b>Resolution order</b> (most to least specific -- corrected 2026-09-27 after the first
     * live look at it; the original interpretation had "Town Proper"/"Outskirts" swapped):
     * <ol>
     *   <li>Inside a real Settlemynts plot -> line 2 is the plot's name, or "Residence" if its zone
     *       type is Private Residence (never publicize a home's custom name to a passerby).
     *   <li>Inside a plot's own buffer (the narrow gap deliberately left between adjacent plots),
     *       not the plot itself -> line 2 is "Town Proper".
     *   <li>Inside the settlement's real "core" polygon, not a plot or its buffer -> line 2 is
     *       "Outskirts" (built-up town, just not a specifically zoned plot).
     *   <li>Inside the settlement's outer padded buffer only (not its real core) -> line 2 is
     *       "No Man's Land" -- the ~10-block band around the settlement's own perimeter.
     *   <li>Nothing settlement-related matched (plain wilderness, or nothing at all) -> line 2
     *       absent.
     * </ol>
     * Line 1 is the settlement's display name if one's registered here, else the surrounding
     * natural region's, else absent -- the same "prefer the more specific match" Region/Settlement
     * merge this class already did before this rework, just computed directly here instead of via
     * a generic per-layer map.
     */
    private LocationSnapshot resolve(ServerLevel level, ServerPlayer player) {
        Set<GeographicEntity> here = Cartography.getEntitiesAt(level, player.getBlockX(), player.getBlockZ());
        if (here.isEmpty()) {
            here = Cartography.discoverNaturalRegion(level, player.blockPosition())
                    .map(Set::of)
                    .orElse(Set.of());
        }

        PlayerKnowledge knowledge = player.getData(ModAttachments.PLAYER_KNOWLEDGE);
        GeographicEntity region = null;
        GeographicEntity settlementPadded = null;
        GeographicEntity settlementCore = null;
        GeographicEntity plot = null;
        GeographicEntity plotBuffer = null;

        for (GeographicEntity entity : here) {
            boolean isZoneEntity = entity.layerId().equals(SETTLEMENTS_ZONE_LAYER_ID);
            if (!isZoneEntity) {
                knowledge.markVisited(entity.id().value());
            }

            // Cartography.getEntitiesAt doesn't filter by LifecycleState itself (confirmed against
            // the decompiled source, see Cartographyr's own decisions.md 2026-09-26) -- a RETIRED/
            // PLANNED/ABANDONED/DESTROYED entity would otherwise still resolve here as if it were
            // active. Every consumer of a position query is responsible for this itself.
            if (entity.lifecycleState() != LifecycleState.REALIZED) {
                continue;
            }

            if (entity.layerId().equals(Layer.REGION_ID)) {
                region = entity;
            } else if (entity.layerId().equals(Layer.SETTLEMENT_ID)) {
                if (entity.type().equals(SETTLEMENT_CORE_TYPE)) {
                    settlementCore = entity;
                } else {
                    settlementPadded = entity;
                }
            } else if (isZoneEntity) {
                if (entity.type().equals(PLOT_TYPE)) {
                    plot = entity;
                } else if (entity.type().equals(PLOT_BUFFER_TYPE)) {
                    plotBuffer = entity;
                }
            }
        }

        Optional<String> line1 = settlementPadded != null ? Optional.of(DisplayText.forEntity(settlementPadded))
                : region != null ? Optional.of(DisplayText.forEntity(region))
                : Optional.empty();

        Optional<String> line2;
        if (plot != null) {
            boolean privateResidence = plot.designation().map(PRIVATE_RESIDENCE_LABEL::equals).orElse(false);
            line2 = Optional.of(privateResidence ? "Residence" : plot.name().orElse("Residence"));
        } else if (plotBuffer != null) {
            line2 = Optional.of("Town Proper");
        } else if (settlementCore != null) {
            line2 = Optional.of("Outskirts");
        } else if (settlementPadded != null) {
            line2 = Optional.of("No Man's Land");
        } else {
            line2 = Optional.empty();
        }

        return new LocationSnapshot(line1, line2);
    }

    private void notifyIfChanged(ServerPlayer player, LocationSnapshot snapshot) {
        UUID playerId = player.getUUID();

        if (Objects.equals(snapshot, lastNotified.getOrDefault(playerId, LocationSnapshot.EMPTY))) {
            pending.remove(playerId);
            return;
        }

        if (!Objects.equals(snapshot, pending.get(playerId))) {
            // First sighting of this snapshot -- wait for a second consecutive match before
            // announcing it, rather than firing immediately.
            pending.put(playerId, snapshot);
            return;
        }

        // Confirmed: this snapshot was also seen on the previous check.
        pending.remove(playerId);
        lastNotified.put(playerId, snapshot);
        sendLocation(player, snapshot);
    }

    private void sendLocation(ServerPlayer player, LocationSnapshot snapshot) {
        LyfeMod.LOGGER.info("Player {} location updated: line1={}, line2={}",
                player.getName().getString(), snapshot.line1(), snapshot.line2());
        PacketDistributor.sendToPlayer(player, new LocationPayload(snapshot.line1(), snapshot.line2()));
    }
}
