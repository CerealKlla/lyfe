package com.github.cerealklla.lyfe.minimap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;

/**
 * In-progress (not-yet-finalized) Settlemynts plot/settlement stakes -- these are never registered
 * with Cartographyr at all (only a *finalized* plot/settlement is, see {@link MinimapTracker}), so
 * they'd otherwise be invisible on the minimap the entire time a Town Planner is actively staking
 * one out (real gap, flagged live 2026-09-29: "Stakes that aren't finalized yet aren't showing up
 * in the mini map either"). Read entirely client-side, no network payload needed: these ghost
 * marker entities are already synced to the client for whoever's allowed to see them (Settlemynts'
 * own {@code broadcastToPlayer}, gated to that settlement's current Town Planners) -- the client's
 * own {@link Level#getEntities} therefore already only ever contains entities this player has
 * permission to see, the same way {@code settlemynts.zone.client.PlotValidityOverlay} already reads
 * its own stakes client-side.
 *
 * <p>Looked up purely by a hardcoded {@link Identifier}, not a compiled class reference -- Lyfe has
 * no dependency on Settlemynts at all (unlike Cartographyr), so this is the same soft, string-based
 * contract {@code location.LocationTracker}/{@link MinimapTracker} already use for Cartographyr
 * entity types, just applied to a raw entity type instead of geographic data. A cached {@code
 * Optional.empty()} (Settlemynts not loaded, or a future rename) just means no points are ever
 * found -- never a crash.
 */
final class ClientGhostMarkerOutlines {

    private static final Map<Identifier, Optional<EntityType<?>>> TYPE_CACHE = new HashMap<>();

    private ClientGhostMarkerOutlines() {
    }

    /** A ghost marker's (dx, dz) offset from the player, plus the {@link Block} it's currently floating -- see {@code pointsWithBlockNear}. */
    record ColoredPoint(int dx, int dz, Block block) {
    }

    /**
     * Same as {@link #pointsNear}, but for a {@code Display.BlockDisplay}-based marker type whose
     * floating block itself carries meaning (e.g. Blueprynts' Building Locator preview: yellow/green/
     * red stained glass) -- reads each entity's own block state back via {@link
     * ClientGhostBlockDisplays}, still with no compiled dependency on whichever mod spawned it, since
     * {@code Display.BlockDisplay} itself is vanilla.
     */
    static List<ColoredPoint> pointsWithBlockNear(Level level, Player player, Identifier entityTypeId, int radius) {
        Optional<EntityType<?>> type = TYPE_CACHE.computeIfAbsent(entityTypeId, BuiltInRegistries.ENTITY_TYPE::getOptional);
        if (type.isEmpty()) {
            return List.of();
        }
        AABB box = player.getBoundingBox().inflate(radius, level.getMaxY(), radius);
        @SuppressWarnings("unchecked")
        EntityType<Entity> castType = (EntityType<Entity>) type.get();
        List<Entity> found = level.getEntities(castType, box, e -> e instanceof Display.BlockDisplay);
        List<ColoredPoint> points = new ArrayList<>(found.size());
        for (Entity entity : found) {
            Block block = ClientGhostBlockDisplays.getBlockState((Display.BlockDisplay) entity).getBlock();
            points.add(new ColoredPoint((int) Math.round(entity.getX() - player.getX()),
                    (int) Math.round(entity.getZ() - player.getZ()), block));
        }
        return points;
    }

    /** Every currently-visible instance of {@code entityTypeId} within {@code radius} blocks, as (dx, dz) integer offsets from the player. */
    static List<int[]> pointsNear(Level level, Player player, Identifier entityTypeId, int radius) {
        Optional<EntityType<?>> type = TYPE_CACHE.computeIfAbsent(entityTypeId, BuiltInRegistries.ENTITY_TYPE::getOptional);
        if (type.isEmpty()) {
            return List.of();
        }
        AABB box = player.getBoundingBox().inflate(radius, level.getMaxY(), radius);
        @SuppressWarnings("unchecked")
        EntityType<Entity> castType = (EntityType<Entity>) type.get();
        List<Entity> found = level.getEntities(castType, box, e -> true);
        List<int[]> points = new ArrayList<>(found.size());
        for (Entity entity : found) {
            points.add(new int[] {(int) Math.round(entity.getX() - player.getX()), (int) Math.round(entity.getZ() - player.getZ())});
        }
        return points;
    }
}
