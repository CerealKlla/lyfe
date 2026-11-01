package com.github.cerealklla.lyfe.summoning;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.TagValueOutput;

import com.github.cerealklla.lyfe.LyfeMod;

/**
 * Generic entity-state capture for later re-spawning via {@code EntityType#loadEntityRecursive} --
 * extracted 2026-10-31 from the identical logic duplicated in {@link AllayCapture#capture} and
 * {@link SummoningStoneData#capture} (both already entity-type-agnostic, just never actually shared)
 * so the new Tavern Companion Token can reuse it directly instead of adding a third copy. Those two
 * methods now delegate here; their own callers are unchanged.
 */
public final class EntityCapture {

    private EntityCapture() {
    }

    public static CompoundTag capture(Entity entity, ServerLevel level) {
        ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(entity.problemPath(), LyfeMod.LOGGER);
        try {
            TagValueOutput output = TagValueOutput.createWithContext(reporter, level.registryAccess());
            entity.save(output);
            CompoundTag tag = output.buildResult();
            tag.remove("UUID");
            return tag;
        } finally {
            reporter.close();
        }
    }
}
