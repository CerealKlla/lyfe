package com.github.cerealklla.lyfe.minimap;

import java.util.function.Consumer;

import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multisets;
import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;

/**
 * Client-side, asynchronous real-terrain sampling for the minimap ({@link MinimapOverlay}) -- the
 * same per-column dominant-MapColor/Brightness technique as {@code knowledge.TerrainMapRenderer}
 * (built for the sign/map feature, itself verified against the decompiled MC 26.1.2 source), but
 * pointed at the client's own already-loaded {@link ClientLevel} instead of a {@code ServerLevel},
 * so there is no server compute and no network traffic for terrain at all -- the same technique
 * real minimap mods use, per the user's own suggestion.
 *
 * <p>Runs on {@link Util#backgroundExecutor()}, reading block/chunk data concurrently with the
 * render thread -- the same category of access vanilla's own chunk-mesh-rebuild workers already
 * perform on this exact data from background threads, so this isn't introducing a new kind of risk
 * to this codebase's usual single-threaded assumptions. Never touches an unloaded chunk (there is
 * no client-side chunk generation to trigger) -- {@code chunk.isEmpty()} just skips that cell,
 * leaving it transparent, exactly like the server-side algorithm this was ported from.
 */
public final class ClientTerrainSampler {

    /** Default/minimap output resolution, independent of real-world radius -- a larger radius just means more blocks per pixel, the same "scale" concept vanilla's own maps use. */
    public static final int IMAGE_SIZE = 128;

    private ClientTerrainSampler() {
    }

    /** Samples an {@code IMAGE_SIZE x IMAGE_SIZE} image covering {@code radius} blocks in every direction from {@code (centerX, centerZ)}, then hands the result to {@code onComplete} on the render thread. */
    public static void sampleAsync(ClientLevel level, int centerX, int centerZ, int radius, Consumer<NativeImage> onComplete) {
        sampleAsync(level, centerX, centerZ, radius, IMAGE_SIZE, false, onComplete);
    }

    /**
     * Same as the fixed-resolution overload, but lets the caller choose the output grid size and
     * whether to apply slope-based shading -- added 2026-10-07 for {@code map.client.MapScreen}.
     * {@code imageSize}: the minimap's fixed 128x128 grid looked visibly blocky once stretched across
     * a near-full-screen viewport (coarse {@code scaleBlocks} cells, each one upscaled via
     * nearest-neighbor blit into a thick, ugly stripe -- found live). Total real blocks actually
     * sampled is governed by {@code radius} alone (roughly {@code (radius*2)^2}, independent of
     * {@code imageSize} -- a bigger grid just divides the same sampled area into smaller, less visible
     * cells), so this costs effectively nothing extra to call with a larger size.
     *
     * <p>{@code flatShading}: even at a larger grid, the {@link MapColor.Brightness} HIGH/LOW
     * per-column slope shading below (ported from vanilla's own {@code MapItem#update}, and real
     * vanilla filled maps show the same contour-line texture) still read as unwanted "lines" once
     * live-tested on the Map screen's much bigger, more zoomed-in-feeling viewport -- user's own
     * explicit call ("flatten it") after seeing it live. {@code true} forces every column to {@code
     * Brightness.NORMAL}, for a flatter, cleaner look; the minimap keeps the textured default.
     */
    public static void sampleAsync(ClientLevel level, int centerX, int centerZ, int radius, int imageSize, boolean flatShading, Consumer<NativeImage> onComplete) {
        sampleAsync(level, centerX, centerZ, radius, radius, imageSize, imageSize, flatShading, onComplete);
    }

    /**
     * Same as the square overload, but with independent X/Z real-world radii and image dimensions --
     * added 2026-10-07 for {@code map.client.MapScreen} after live-testing the square-sample-stretched-
     * to-a-wide-rectangle version: it filled the whole widescreen window per the user's own request
     * ("stretch proportionally to fit into the resolution"), but a non-uniform stretch of a square
     * sample onto a wider rectangle visibly distorted the terrain ("slightly stretched horizontally").
     * The real fix is sampling a wider real-world area to begin with -- more world shown horizontally
     * on a wide window, not the same square world view stretched -- so the caller (the Map screen)
     * derives {@code radiusX}/{@code imageWidth} from its own viewport aspect ratio, keeping a single
     * uniform blocks-per-pixel scale on both axes.
     */
    public static void sampleAsync(ClientLevel level, int centerX, int centerZ, int radiusX, int radiusZ,
            int imageWidth, int imageHeight, boolean flatShading, Consumer<NativeImage> onComplete) {
        Util.backgroundExecutor().execute(() -> {
            NativeImage image = sample(level, centerX, centerZ, radiusX, radiusZ, imageWidth, imageHeight, flatShading);
            Minecraft.getInstance().execute(() -> onComplete.accept(image));
        });
    }

    private static NativeImage sample(ClientLevel level, int centerX, int centerZ, int radiusX, int radiusZ,
            int imageWidth, int imageHeight, boolean flatShading) {
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, imageWidth, imageHeight, false);
        sampleGrid(level, centerX, centerZ, radiusX, radiusZ, imageWidth, imageHeight, flatShading,
                (col, row, color, brightness) -> image.setPixelABGR(col, row, argbToAbgr(color.calculateARGBColor(brightness))));
        return image;
    }

    /** Receives one sampled cell at a time -- see {@link #sampleGrid}. */
    @FunctionalInterface
    public interface CellWriter {
        void write(int col, int row, MapColor color, MapColor.Brightness brightness);
    }

    /**
     * Samples a {@code cols x rows} grid covering {@code radiusX}/{@code radiusZ} blocks around
     * {@code (centerX, centerZ)}, same technique/column algorithm as before, but reporting each
     * cell's resolved {@link MapColor}/{@link MapColor.Brightness} to {@code writer} instead of only
     * ever writing straight into a {@link NativeImage} -- extracted 2026-10-07 so {@code
     * map.cache.TerrainCachePassiveSampler} can reuse the exact same sampling/shading logic to fill
     * the persisted terrain cache (as palette-encoded shorts) without duplicating it. Synchronous --
     * callers needing this off the render thread wrap it themselves (see {@link #sample} and {@code
     * TerrainCachePassiveSampler}, both of which already run on a background thread).
     */
    public static void sampleGrid(ClientLevel level, int centerX, int centerZ, int radiusX, int radiusZ,
            int cols, int rows, boolean flatShading, CellWriter writer) {
        int scaleBlocksX = Math.max(1, Math.round((radiusX * 2.0f) / cols));
        int scaleBlocksZ = Math.max(1, Math.round((radiusZ * 2.0f) / rows));
        BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos belowPos = new BlockPos.MutableBlockPos();

        for (int col = 0; col < cols; col++) {
            double previousAverageAreaHeight = 0.0;

            for (int row = 0; row < rows; row++) {
                int areaMinX = centerX + (col - cols / 2) * scaleBlocksX;
                int areaMinZ = centerZ + (row - rows / 2) * scaleBlocksZ;

                LevelChunk chunk = level.getChunk(SectionPos.blockToSectionCoord(areaMinX), SectionPos.blockToSectionCoord(areaMinZ));
                if (chunk.isEmpty()) {
                    continue; // Not loaded client-side -- leave the cell unwritten rather than guessing.
                }

                Multiset<MapColor> colorCount = LinkedHashMultiset.create();
                int waterDepth = 0;
                double averageAreaHeight = 0.0;
                for (int dx = 0; dx < scaleBlocksX; dx++) {
                    for (int dz = 0; dz < scaleBlocksZ; dz++) {
                        blockPos.set(areaMinX + dx, 0, areaMinZ + dz);
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
                        averageAreaHeight += (double) columnY / (scaleBlocksX * scaleBlocksZ);
                        colorCount.add(state.getMapColor(level, blockPos));
                    }
                }
                waterDepth /= scaleBlocksX * scaleBlocksZ;

                MapColor color = Iterables.getFirst(Multisets.copyHighestCountFirst(colorCount), MapColor.NONE);
                MapColor.Brightness brightness;
                if (flatShading) {
                    brightness = MapColor.Brightness.NORMAL;
                } else if (color == MapColor.WATER) {
                    double diff = waterDepth * 0.1;
                    brightness = diff < 0.5 ? MapColor.Brightness.HIGH : diff > 0.9 ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
                } else {
                    double diff = (averageAreaHeight - previousAverageAreaHeight) * 4.0 / (scaleBlocksZ + 4);
                    brightness = diff > 0.6 ? MapColor.Brightness.HIGH : diff < -0.6 ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
                }
                previousAverageAreaHeight = averageAreaHeight;

                writer.write(col, row, color, brightness);
            }
        }
    }

    /** Ported from {@code MapItem#getCorrectStateForFluidBlock} (private there), same as {@code knowledge.TerrainMapRenderer}'s own copy. */
    private static BlockState correctStateForFluidBlock(ClientLevel level, BlockState state, BlockPos pos) {
        FluidState fluidState = state.getFluidState();
        return !fluidState.isEmpty() && !state.isFaceSturdy(level, pos, Direction.UP) ? fluidState.createLegacyBlock() : state;
    }

    /** {@link NativeImage#setPixelABGR} expects byte order opposite {@link MapColor#calculateARGBColor} -- a plain channel swap, no library helper found for this exact pair in this MC version. */
    private static int argbToAbgr(int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        return (a << 24) | (b << 16) | (g << 8) | r;
    }
}
