package com.github.cerealklla.lyfe.loot;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.github.cerealklla.lyfe.cook.FoodTierLadder;
import com.github.cerealklla.lyfe.cook.RecipeNoteConstants;
import com.github.cerealklla.lyfe.cook.RecipeNoteTarget;
import com.github.cerealklla.lyfe.registration.ModItems;

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
 * Injects a bonus "Recipe Notes" pool into the same fixed set of vanilla chest loot tables -- direct
 * mirror of {@code research.ResearchNoteLootInjector}'s mechanism (same reflective loot-table-append
 * technique, same rationale for why {@code LootTableLoadEvent} is simpler than {@code
 * IGlobalLootModifier} here), kept as a separate injector (not merged into the equipment one) so its
 * own, deliberately higher {@code RecipeNoteConstants#POOL_CHANCE} doesn't also apply to Research
 * Notes.
 */
public final class RecipeNoteLootInjector {

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

    private RecipeNoteLootInjector() {
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
            return table;
        }
    }

    private static LootPool buildBonusPool() {
        LootPool.Builder builder = LootPool.lootPool()
                .setRolls(ConstantValue.exactly(1.0F))
                .when(LootItemRandomChanceCondition.randomChance(RecipeNoteConstants.POOL_CHANCE));
        for (int tier = FoodTierLadder.MIN_TIER; tier <= FoodTierLadder.MAX_TIER; tier++) {
            for (Identifier resultId : allCandidatesForTier(tier)) {
                builder.add(LootItem.lootTableItem(ModItems.RECIPE_NOTES.get())
                        .setWeight(RecipeNoteConstants.TIER_WEIGHTS[tier - 1])
                        .apply(SetComponentsFunction.setComponent(
                                ModItems.RECIPE_NOTE_TARGET.get(), new RecipeNoteTarget(resultId, tier))));
            }
        }
        return builder.build();
    }

    private static List<Identifier> allCandidatesForTier(int tier) {
        List<Identifier> ids = new ArrayList<>(FoodTierLadder.trackA(tier));
        ids.add(FoodTierLadder.trackB(tier));
        return ids;
    }
}
