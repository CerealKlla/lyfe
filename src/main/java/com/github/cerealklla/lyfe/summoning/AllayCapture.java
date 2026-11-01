package com.github.cerealklla.lyfe.summoning;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/**
 * Captures a live Allay's complete state for later re-spawning via {@code
 * EntityType#loadEntityRecursive}. Thin wrapper over {@link EntityCapture#capture} (extracted
 * 2026-10-31, since this was an exact duplicate of {@link SummoningStoneData#capture}) -- kept as
 * its own method so existing call sites don't need touching.
 */
public final class AllayCapture {

    private AllayCapture() {
    }

    public static CompoundTag capture(Entity allay, ServerLevel level) {
        return EntityCapture.capture(allay, level);
    }
}
