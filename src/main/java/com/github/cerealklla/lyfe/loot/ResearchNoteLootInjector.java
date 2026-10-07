package com.github.cerealklla.lyfe.loot;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.github.cerealklla.lyfe.craft.EquipmentTierLadder;
import com.github.cerealklla.lyfe.registration.ModItems;
import com.github.cerealklla.lyfe.research.ResearchNoteConstants;
import com.github.cerealklla.lyfe.research.ResearchNoteTarget;

import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKeySet;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.SetComponentsFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.neoforged.neoforge.event.LootTableLoadEvent;

/**
 * Injects a bonus "Research Notes" pool into a fixed set of vanilla chest loot tables (2026-10-03,
 * user request) via {@link LootTableLoadEvent} (confirmed still present/unchanged in NeoForge
 * 26.1.2.109, simpler for this fixed, known use case than the newer {@code IGlobalLootModifier}
 * datapack-registry path, which has no built-in "add one item" modifier).
 *
 * <p>No public NeoForge/vanilla API adds a pool to an already-built {@link LootTable}: its {@code
 * pools} field is private with no accessor, and {@code LootTable.Builder} only accepts pool
 * *builders*, never an already-built {@link LootPool} -- confirmed via {@code javap} against the
 * real 26.1.2 server jar before writing this. {@link #appendPool} reflectively reads the loaded
 * table's four private fields and reconstructs it via its private constructor with one extra pool
 * appended, the same "cached reflective field read, no public API exists" precedent already used
 * for {@code AbstractSignEditScreen#sign} (see decisions.md, 2026-09-25).
 */
public final class ResearchNoteLootInjector {

    private static final Set<Identifier> TARGET_TABLES = Set.of(
            Identifier.withDefaultNamespace("chests/simple_dungeon"),
            Identifier.withDefaultNamespace("chests/abandoned_mineshaft"),
            Identifier.withDefaultNamespace("chests/stronghold_corridor"),
            Identifier.withDefaultNamespace("chests/stronghold_crossing"),
            Identifier.withDefaultNamespace("chests/stronghold_library"),
            Identifier.withDefaultNamespace("chests/nether_bridge"),
            Identifier.withDefaultNamespace("chests/end_city_treasure"),
            Identifier.withDefaultNamespace("chests/buried_treasure"),
            Identifier.withDefaultNamespace("chests/shipwreck_supply"),
            Identifier.withDefaultNamespace("chests/shipwreck_treasure"),
            Identifier.withDefaultNamespace("chests/shipwreck_map"));

    private static final Field PARAM_SET_FIELD;
    private static final Field RANDOM_SEQUENCE_FIELD;
    private static final Field POOLS_FIELD;
    private static final Field FUNCTIONS_FIELD;
    private static final Constructor<LootTable> CONSTRUCTOR;

    static {
        try {
            PARAM_SET_FIELD = LootTable.class.getDeclaredField("paramSet");
            PARAM_SET_FIELD.setAccessible(true);
            RANDOM_SEQUENCE_FIELD = LootTable.class.getDeclaredField("randomSequence");
            RANDOM_SEQUENCE_FIELD.setAccessible(true);
            POOLS_FIELD = LootTable.class.getDeclaredField("pools");
            POOLS_FIELD.setAccessible(true);
            FUNCTIONS_FIELD = LootTable.class.getDeclaredField("functions");
            FUNCTIONS_FIELD.setAccessible(true);
            CONSTRUCTOR = LootTable.class.getDeclaredConstructor(
                    ContextKeySet.class, Optional.class, List.class, List.class);
            CONSTRUCTOR.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private ResearchNoteLootInjector() {
    }

    public static void onLootTableLoad(LootTableLoadEvent event) {
        if (!TARGET_TABLES.contains(event.getName())) {
            return;
        }
        event.setTable(appendPool(event.getTable(), buildBonusPool()));
    }

    @SuppressWarnings("unchecked")
    private static LootTable appendPool(LootTable table, LootPool extra) {
        try {
            ContextKeySet paramSet = (ContextKeySet) PARAM_SET_FIELD.get(table);
            Optional<Identifier> randomSequence = (Optional<Identifier>) RANDOM_SEQUENCE_FIELD.get(table);
            List<LootPool> pools = new ArrayList<>((List<LootPool>) POOLS_FIELD.get(table));
            pools.add(extra);
            List<LootItemFunction> functions = (List<LootItemFunction>) FUNCTIONS_FIELD.get(table);
            return CONSTRUCTOR.newInstance(paramSet, randomSequence, pools, functions);
        } catch (ReflectiveOperationException e) {
            // Best-effort -- if this version's LootTable shape ever changes underneath us, skip the
            // bonus pool rather than crash loot generation for every table in the game.
            return table;
        }
    }

    private static LootPool buildBonusPool() {
        LootPool.Builder builder = LootPool.lootPool()
                .setRolls(ConstantValue.exactly(1.0F))
                .when(LootItemRandomChanceCondition.randomChance(ResearchNoteConstants.POOL_CHANCE));
        // Starts at 1, not EquipmentTierLadder.MIN_TIER (0 since Wood joined the generated-recipe
        // system 2026-10-03): Research Notes have no reason to exist for Wood, which is auto-known
        // and never enters PlayerResearch at all -- and TIER_WEIGHTS is still only sized for Tiers
        // 1-5 (indexed tier - 1), so including Tier 0 here would throw besides. Bounded at
        // MAX_REACHABLE_TIER (not MAX_TIER, 2026-10-03) -- Legendary (the soft/placeholder Tier 6
        // added alongside Copper's insertion) should never drop a Research Note naturally, and
        // TIER_WEIGHTS is still only a 5-element array besides.
        for (EquipmentTierLadder.ToolType tool : EquipmentTierLadder.ToolType.values()) {
            for (int tier = 1; tier <= EquipmentTierLadder.MAX_REACHABLE_TIER; tier++) {
                Identifier resultId = EquipmentTierLadder.itemId(tool, tier);
                builder.add(LootItem.lootTableItem(ModItems.RESEARCH_NOTES.get())
                        .setWeight(ResearchNoteConstants.TIER_WEIGHTS[tier - 1])
                        .apply(SetComponentsFunction.setComponent(
                                ModItems.RESEARCH_NOTE_TARGET.get(), new ResearchNoteTarget(resultId, tier))));
            }
        }
        // Armor (added 2026-10-04) -- same loop, same tier bounds, no ToolType at all.
        for (var armorType : EquipmentTierLadder.ARMOR_TYPES) {
            for (int tier = 1; tier <= EquipmentTierLadder.MAX_REACHABLE_TIER; tier++) {
                Identifier resultId = EquipmentTierLadder.armorItemId(armorType, tier);
                builder.add(LootItem.lootTableItem(ModItems.RESEARCH_NOTES.get())
                        .setWeight(ResearchNoteConstants.TIER_WEIGHTS[tier - 1])
                        .apply(SetComponentsFunction.setComponent(
                                ModItems.RESEARCH_NOTE_TARGET.get(), new ResearchNoteTarget(resultId, tier))));
            }
        }
        return builder.build();
    }
}
