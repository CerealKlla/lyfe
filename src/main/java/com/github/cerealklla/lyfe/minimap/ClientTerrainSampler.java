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

    /** Fixed output resolution, independent of real-world radius -- a larger radius just means more blocks per pixel, the same "scale" concept vanilla's own maps use. */
    public static final int IMAGE_SIZE = 128;

    private ClientTerrainSampler() {
    }

    /** Samples an {@code IMAGE_SIZE x IMAGE_SIZE} image covering {@code radius} blocks in every direction from {@code (centerX, centerZ)}, then hands the result to {@code onComplete} on the render thread. */
    public static void sampleAsync(ClientLevel level, int centerX, int centerZ, int radius, Consumer<NativeImage> onComplete) {
        Util.backgroundExecutor().execute(() -> {
            NativeImage image = sample(level, centerX, centerZ, radius);
            Minecraft.getInstance().execute(() -> onComplete.accept(image));
        });
    }

    private static NativeImage sample(ClientLevel level, int centerX, int centerZ, int radius) {
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, IMAGE_SIZE, IMAGE_SIZE, false);
        int scaleBlocks = Math.max(1, Math.round((radius * 2.0f) / IMAGE_SIZE));
        BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos belowPos = new BlockPos.MutableBlockPos();

        for (int imgX = 0; imgX < IMAGE_SIZE; imgX++) {
            double previousAverageAreaHeight = 0.0;

            for (int imgZ = 0; imgZ < IMAGE_SIZE; imgZ++) {
                int areaMinX = centerX + (imgX - IMAGE_SIZE / 2) * scaleBlocks;
                int areaMinZ = centerZ + (imgZ - IMAGE_SIZE / 2) * scaleBlocks;

                LevelChunk chunk = level.getChunk(SectionPos.blockToSectionCoord(areaMinX), SectionPos.blockToSectionCoord(areaMinZ));
                if (chunk.isEmpty()) {
                    continue; // Not loaded client-side -- leave transparent rather than guessing.
                }

                Multiset<MapColor> colorCount = LinkedHashMultiset.create();
                int waterDepth = 0;
                double averageAreaHeight = 0.0;
                for (int dx = 0; dx < scaleBlocks; dx++) {
                    for (int dz = 0; dz < scaleBlocks; dz++) {
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
                        averageAreaHeight += (double) columnY / (scaleBlocks * scaleBlocks);
                        colorCount.add(state.getMapColor(level, blockPos));
                    }
                }
                waterDepth /= scaleBlocks * scaleBlocks;

                MapColor color = Iterables.getFirst(Multisets.copyHighestCountFirst(colorCount), MapColor.NONE);
                MapColor.Brightness brightness;
                if (color == MapColor.WATER) {
                    double diff = waterDepth * 0.1;
                    brightness = diff < 0.5 ? MapColor.Brightness.HIGH : diff > 0.9 ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
                } else {
                    double diff = (averageAreaHeight - previousAverageAreaHeight) * 4.0 / (scaleBlocks + 4);
                    brightness = diff > 0.6 ? MapColor.Brightness.HIGH : diff < -0.6 ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
                }
                previousAverageAreaHeight = averageAreaHeight;

                image.setPixelABGR(imgX, imgZ, argbToAbgr(color.calculateARGBColor(brightness)));
            }
        }
        return image;
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
