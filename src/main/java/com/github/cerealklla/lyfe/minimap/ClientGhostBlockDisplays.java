package com.github.cerealklla.lyfe.minimap;

import java.lang.reflect.Field;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Reads a {@link Display.BlockDisplay}'s own floating block state back client-side -- the read-side
 * mirror of Settlemynts'/Blueprynts' own {@code GhostBlockDisplays.setBlockState} write-side helper
 * (same reflection technique: {@code getBlockState()} and its synced-data accessor are both private
 * on the vanilla class). Used by {@link ClientGhostMarkerOutlines} so the minimap can color a ghost
 * marker dot by whatever block it's floating (e.g. Blueprynts' Building Locator preview: yellow/
 * green/red stained glass) without any compiled dependency on the mod that spawned it -- {@code
 * Display.BlockDisplay} itself is vanilla, so no cross-mod class reference is needed at all.
 */
final class ClientGhostBlockDisplays {

    private static volatile EntityDataAccessor<BlockState> blockStateAccessor;

    private ClientGhostBlockDisplays() {
    }

    static BlockState getBlockState(Display.BlockDisplay entity) {
        return entity.getEntityData().get(blockStateAccessor());
    }

    @SuppressWarnings("unchecked")
    private static EntityDataAccessor<BlockState> blockStateAccessor() {
        EntityDataAccessor<BlockState> accessor = blockStateAccessor;
        if (accessor == null) {
            synchronized (ClientGhostBlockDisplays.class) {
                accessor = blockStateAccessor;
                if (accessor == null) {
                    try {
                        Field field = Display.BlockDisplay.class.getDeclaredField("DATA_BLOCK_STATE_ID");
                        field.setAccessible(true);
                        accessor = (EntityDataAccessor<BlockState>) field.get(null);
                        blockStateAccessor = accessor;
                    } catch (ReflectiveOperationException e) {
                        throw new IllegalStateException("Could not resolve Display.BlockDisplay's block state accessor", e);
                    }
                }
            }
        }
        return accessor;
    }
}
