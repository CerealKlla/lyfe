package com.github.cerealklla.lyfe.fishing;

import java.util.Map;

import com.github.cerealklla.lyfe.LyfeMod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;

/**
 * Places a {@link ShipwreckMarkerEntity} the first time a naturally-generated Shipwreck's own
 * origin chunk loads (design doc Section 28, 2026-10-24) -- mirrors Settlemynts' {@code
 * zone.NaturalVillagePlotListener} almost exactly (same chunk-load trigger, same structure-tag
 * filter, same "fires on every load, a persisted index makes it idempotent" shape). Fires once per
 * structure instance in practice (a structure's reference only lives in its own origin chunk's
 * {@code getAllStarts()}), and {@link ShipwreckMarkerIndex} makes a repeat trigger (e.g. the same
 * chunk reloading across sessions) a cheap no-op regardless.
 */
public final class ShipwreckMarkerListener {

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        Map<Structure, StructureStart> starts = event.getChunk().getAllStarts();
        if (starts.isEmpty()) {
            return;
        }

        var structureRegistry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        ShipwreckMarkerIndex index = ShipwreckMarkerIndex.get(level.getServer());

        for (Map.Entry<Structure, StructureStart> entry : starts.entrySet()) {
            StructureStart start = entry.getValue();
            if (!start.isValid()) {
                continue;
            }
            if (!structureRegistry.wrapAsHolder(entry.getKey()).is(StructureTags.SHIPWRECK)) {
                continue;
            }

            BlockPos structureStartPos = start.getChunkPos().getWorldPosition();
            if (index.isHandled(structureStartPos)) {
                continue;
            }

            BoundingBox box = start.getBoundingBox();
            BlockPos markerPos = box.getCenter();
            ShipwreckMarkerEntity.create(level, markerPos);
            index.markHandled(structureStartPos);
            LyfeMod.LOGGER.info("Shipwreck marker placed at {} (structure origin {})", markerPos, structureStartPos);
        }
    }
}
