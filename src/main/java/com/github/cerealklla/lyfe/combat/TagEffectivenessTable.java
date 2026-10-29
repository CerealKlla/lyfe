package com.github.cerealklla.lyfe.combat;

import java.util.Collection;
import java.util.Map;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

/**
 * One generic (tag x entity-type tag) -&gt; effectiveness-percent lookup, shared by spells
 * ({@code magician.SpellEffect}) and Runewords ({@code socket.RunewordEffect}) -- both the hard-
 * block case (0%, e.g. {@code heal_organic} vs {@code caryvan:non_organic}) and the weakness/
 * resistance case (150%, e.g. {@code life} vs {@code minecraft:undead}) are the same table, just
 * different tag sources feeding into it. Plain hand-coded data, same shape as {@code
 * craft.EquipmentTierLadder}/{@code craft.ComponentGroups} -- not datapack-driven, not routed
 * through {@code Nuclyus.PersistedServerStore} (reserved for randomly-generated-then-persisted
 * data, which this fixed table is not).
 */
public final class TagEffectivenessTable {

    private static final TagKey<EntityType<?>> NON_ORGANIC =
            TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath("caryvan", "non_organic"));

    private static final Map<String, Map<TagKey<EntityType<?>>, Integer>> TABLE = Map.of(
            "heal_organic", Map.of(NON_ORGANIC, 0),
            "damage_organic", Map.of(NON_ORGANIC, 0),
            "life", Map.of(EntityTypeTags.UNDEAD, 150)
    );

    private TagEffectivenessTable() {
    }

    /**
     * Combined multiplier (1.0 = no change) across every tag in {@code tags} that has an entry
     * matching one of {@code target}'s own entity-type tags. A tag with no entry at all, or one
     * that doesn't match any of the target's tags, contributes nothing (not a change to 100%).
     */
    public static float multiplierFor(Collection<String> tags, LivingEntity target) {
        float multiplier = 1.0F;
        for (String tag : tags) {
            Map<TagKey<EntityType<?>>, Integer> byEntityTag = TABLE.get(tag);
            if (byEntityTag == null) {
                continue;
            }
            for (Map.Entry<TagKey<EntityType<?>>, Integer> entry : byEntityTag.entrySet()) {
                if (target.getType().builtInRegistryHolder().is(entry.getKey())) {
                    multiplier *= entry.getValue() / 100.0F;
                }
            }
        }
        return multiplier;
    }
}
