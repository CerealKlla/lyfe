package com.github.cerealklla.lyfe.knowledge;

import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multisets;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * Renders a fully-explored vanilla filled map instantly, server-side, instead of requiring a
 * player to walk around "drawing" it incrementally like {@link MapItem#update} does. Ported from
 * that same method's per-cell sampling logic (dominant {@link MapColor} via a {@link Multiset},
 * {@link MapColor.Brightness} from water depth or column-height difference) -- verified against
 * the decompiled MC 26.1.2 source. Deliberately drops everything in the vanilla algorithm that
 * only makes sense for incremental, player-relative exploration: the player-distance radius, the
 * per-tick column throttling, the edge dithering, and the ceiling-dimension (Nether-roof) special
 * case -- this always fills the entire 128x128 grid in one pass.
 *
 * <p>Not unit-testable -- needs a live {@link ServerLevel} with real chunk data, same limitation
 * as {@code natural.NaturalRegionDiscovery} and the rest of {@link SignListener}'s placement
 * logic.
 */
final class TerrainMapRenderer {

    private TerrainMapRenderer() {
    }

    /**
     * Thin wrapper around vanilla's own {@link MapItem#create} factory (the same one the
     * cartography table uses) -- allocates a {@code MapId}, registers a blank {@link
     * MapItemSavedData}, and returns an {@link ItemStack} of {@code Items.FILLED_MAP} carrying it.
     * No live position/frame-marker tracking, since this is a static reference render, not a
     * live-tracking map.
     */
    static ItemStack createFilledMap(ServerLevel level, int centerX, int centerZ, byte scale) {
        return MapItem.create(level, centerX, centerZ, scale, false, false);
    }

    /** Ported from {@code MapItem#getCorrectStateForFluidBlock} (private there). */
    private static BlockState correctStateForFluidBlock(ServerLevel level, BlockState state, BlockPos pos) {
        FluidState fluidState = state.getFluidState();
        return !fluidState.isEmpty() && !state.isFaceSturdy(level, pos, Direction.UP) ? fluidState.createLegacyBlock() : state;
    }

    /** Fills every pixel of {@code data}'s 128x128 grid by sampling the world around {@code (centerX, centerZ)}. */
    static void renderTerrain(ServerLevel level, MapItemSavedData data, int centerX, int centerZ, int scale) {
        int scaleBlocks = 1 << scale;
        BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos belowPos = new BlockPos.MutableBlockPos();

        for (int imgX = 0; imgX < 128; imgX++) {
            double previousAverageAreaHeight = 0.0;

            for (int imgY = 0; imgY < 128; imgY++) {
                int averagingAreaMinX = (centerX / scaleBlocks + imgX - 64) * scaleBlocks;
                int averagingAreaMinZ = (centerZ / scaleBlocks + imgY - 64) * scaleBlocks;
                Multiset<MapColor> colorCount = LinkedHashMultiset.create();
                LevelChunk chunk = level.getChunk(
                        SectionPos.blockToSectionCoord(averagingAreaMinX), SectionPos.blockToSectionCoord(averagingAreaMinZ));
                if (chunk.isEmpty()) {
                    continue;
                }

                int waterDepth = 0;
                double averageAreaHeight = 0.0;
                for (int deltaX = 0; deltaX < scaleBlocks; deltaX++) {
                    for (int deltaZ = 0; deltaZ < scaleBlocks; deltaZ++) {
                        blockPos.set(averagingAreaMinX + deltaX, 0, averagingAreaMinZ + deltaZ);
                        int columnY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, blockPos.getX(), blockPos.getZ()) + 1;
                        BlockState state;
                        if (columnY <= level.getMinY()) {
                            state = Blocks.BEDROCK.defaultBlockState();
                        } else {
                            do {
                                blockPos.setY(--columnY);
                                state = chunk.getBlockState(blockPos);
                            } while (state.getMapColor(level, blockPos) == MapColor.NONE && columnY > level.getMinY());

                            if (columnY > level.getMinY() && !state.getFluidState().isEmpty()) {
                                int solidY = columnY - 1;
                                belowPos.set(blockPos);
                                BlockState belowBlock;
                                do {
                                    belowPos.setY(solidY--);
                                    belowBlock = chunk.getBlockState(belowPos);
                                    waterDepth++;
                                } while (solidY > level.getMinY() && !belowBlock.getFluidState().isEmpty());

                                state = correctStateForFluidBlock(level, state, blockPos);
                            }
                        }

                        averageAreaHeight += (double) columnY / (scaleBlocks * scaleBlocks);
                        colorCount.add(state.getMapColor(level, blockPos));
                    }
                }

                waterDepth /= scaleBlocks * scaleBlocks;
                MapColor color = Iterables.getFirst(Multisets.copyHighestCountFirst(colorCount), MapColor.NONE);
                MapColor.Brightness brightness;
                if (color == MapColor.WATER) {
                    double diff = waterDepth * 0.1;
                    if (diff < 0.5) {
                        brightness = MapColor.Brightness.HIGH;
                    } else if (diff > 0.9) {
                        brightness = MapColor.Brightness.LOW;
                    } else {
                        brightness = MapColor.Brightness.NORMAL;
                    }
                } else {
                    double diff = (averageAreaHeight - previousAverageAreaHeight) * 4.0 / (scaleBlocks + 4);
                    if (diff > 0.6) {
                        brightness = MapColor.Brightness.HIGH;
                    } else if (diff < -0.6) {
                        brightness = MapColor.Brightness.LOW;
                    } else {
                        brightness = MapColor.Brightness.NORMAL;
                    }
                }
                previousAverageAreaHeight = averageAreaHeight;

                data.setColor(imgX, imgY, color.getPackedId(brightness));
            }
        }
    }
}
