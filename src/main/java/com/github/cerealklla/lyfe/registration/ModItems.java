package com.github.cerealklla.lyfe.registration;

import com.github.cerealklla.lyfe.LyfeMod;
import com.github.cerealklla.lyfe.knowledge.KnowledgeReference;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Data components for the Cartographyr-skill sign/map mechanic (design doc Section 9.1). No
 * special items anymore, 2026-09-25 (see decisions.md) -- signs/maps ride on genuinely vanilla
 * items now, see {@code knowledge.SignListener} for the actual placement/reading behavior.
 */
public final class ModItems {

    private ModItems() {
    }

    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, LyfeMod.MODID);

    // Persisted (survives save/load, e.g. the map sitting in an item frame across a restart) and
    // network-synchronized (so the client can render the item's display name/tooltip correctly).
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<KnowledgeReference>> KNOWLEDGE_REFERENCE =
            DATA_COMPONENTS.registerComponentType("knowledge_reference", builder -> builder
                    .persistent(KnowledgeReference.CODEC)
                    .networkSynchronized(KnowledgeReference.STREAM_CODEC));
}
