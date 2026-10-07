package com.github.cerealklla.lyfe.registration;

import com.github.cerealklla.lyfe.LyfeMod;
import com.github.cerealklla.lyfe.rest.SeatEntity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Lyfe's first entity type (design doc, 2026-10-03) -- the invisible seat mount for StoolBlock. */
public final class ModEntities {

    private ModEntities() {
    }

    public static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(LyfeMod.MODID);

    // Never /summon-able -- this is a pure server-side bookkeeping entity, not real world content.
    // Deliberately NOT .noSave(): confirmed against the real decompiled Entity#startRiding source
    // that it refuses to mount a passenger onto any vehicle whose EntityType#canSerialize() is
    // false (a real, non-obvious gotcha -- the exact cause of a 2026-10-03 playtest bug, "right
    // clicking did nothing," since .noSave() sets that flag false). In practice this rarely
    // persists anything anyway -- SeatEntity#tick() discards itself the moment it has no rider, so
    // the only time it's ever actually written to disk is a save/shutdown catching someone mid-sit.
    public static final DeferredHolder<EntityType<?>, EntityType<SeatEntity>> SEAT = ENTITIES.registerEntityType(
            "seat",
            SeatEntity::new,
            MobCategory.MISC,
            builder -> builder.sized(0.2f, 0.2f).noSummon());
}
