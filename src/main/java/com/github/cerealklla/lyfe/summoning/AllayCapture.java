package com.github.cerealklla.lyfe.summoning;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.TagValueOutput;

import com.github.cerealklla.lyfe.LyfeMod;

/**
 * Captures a live Allay's complete state for later re-spawning via {@code
 * EntityType#loadEntityRecursive} -- direct copy of {@link SummoningStoneData#capture}'s own
 * technique (confirmed real bug that fixed: {@code Entity#saveWithoutId} is "without the entity
 * *type* id," not "without the real UUID" -- {@code mount.save(output)} writes the type id first,
 * then the real UUID has to be stripped explicitly afterward so a later summon always gets a
 * genuinely fresh identity).
 */
public final class AllayCapture {

    private AllayCapture() {
    }

    public static CompoundTag capture(Entity allay, ServerLevel level) {
        ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(allay.problemPath(), LyfeMod.LOGGER);
        try {
            TagValueOutput output = TagValueOutput.createWithContext(reporter, level.registryAccess());
            allay.save(output);
            CompoundTag tag = output.buildResult();
            tag.remove("UUID");
            return tag;
        } finally {
            reporter.close();
        }
    }
}
