package com.github.cerealklla.lyfe.socket;

import java.util.Set;

import com.github.cerealklla.lyfe.registration.ModItems;

import com.mojang.serialization.MapCodec;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.neoforged.neoforge.event.LootTableLoadEvent;

/**
 * "10% to find one in dungeon chests and such" (explicit user spec, 2026-10-18) -- "and such" is
 * resolved to the exact same {@code TARGET_TABLES} set {@code loot.ResearchNoteLootInjector} already
 * established for this suite's own definition of "dungeon-ish loot location" (simple dungeon,
 * abandoned mineshaft, both stronghold variants, nether bridge, end city, buried/shipwreck treasure),
 * rather than inventing a new, different list -- same reflective append-a-pool technique (see {@link
 * LootTableReflection}).
 *
 * <p>Uses a dynamic {@link LootItemFunction} ({@link RandomRuneFunction}) rather than one static
 * per-{@link RuneId} weighted entry per {@code loot.ResearchNoteLootInjector}'s own shape --
 * deliberately: {@link LootTableLoadEvent} fires during the early datapack-reload step, well before
 * {@code ServerStartingEvent} (confirmed by this suite's own established convention: {@code
 * craft.VanillaRecipeStripper} already treats {@code ModifyRecipeJsonsEvent}/by extension this event
 * as a separate, earlier hook from {@code onServerStarting}), so {@link ServerRunewordStore}'s real
 * randomized rune-sequence mapping -- and so {@link RuneWorth}'s own worth table -- isn't ready yet
 * at table-load time. Baking static per-rune weights here would freeze in stale (all-zero/uniform)
 * worth for the rest of the server's life. A dynamic function instead re-rolls {@link
 * RuneWorth#rollRandomRune} fresh every time a real chest is actually opened, long after both
 * Runewords and worth are real.
 */
public final class RuneLootInjector {

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

    private static final float CHANCE = 0.10F;

    public static void onLootTableLoad(LootTableLoadEvent event) {
        if (!TARGET_TABLES.contains(event.getName())) {
            return;
        }
        LootPool pool = LootPool.lootPool()
                .setRolls(ConstantValue.exactly(1.0F))
                .when(LootItemRandomChanceCondition.randomChance(CHANCE))
                .add(LootItem.lootTableItem(ModItems.RUNE.get()).apply(() -> new RandomRuneFunction()))
                .build();
        event.setTable(LootTableReflection.withExtraPool(event.getTable(), pool));
    }

    /** Sets {@code ModItems#RUNE_ID} to a fresh {@link RuneWorth#rollRandomRune} pick every time it actually runs -- never serialized, only ever constructed here and appended directly to an in-memory {@link net.minecraft.world.level.storage.loot.LootTable}. */
    private record RandomRuneFunction() implements LootItemFunction {
        @Override
        public ItemStack apply(ItemStack stack, LootContext context) {
            ItemStack result = stack.copy();
            result.set(ModItems.RUNE_ID.get(), RuneWorth.rollRandomRune(context.getRandom()));
            return result;
        }

        @Override
        public MapCodec<? extends LootItemFunction> codec() {
            throw new UnsupportedOperationException("RandomRuneFunction is never serialized");
        }
    }
}
