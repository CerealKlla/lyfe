package com.github.cerealklla.lyfe.socket;

import java.util.Set;

import com.github.cerealklla.lyfe.registration.ModItems;

import com.mojang.serialization.MapCodec;

import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.neoforged.neoforge.event.LootTableLoadEvent;

/**
 * "Found weapons/tools/armor have a small chance to spawn with sockets as well, as the # of sockets
 * increases the rarity increases. No item will drop with sockets which already have things inserted
 * into them" (explicit user spec, 2026-10-18).
 *
 * <p><b>Scope, deliberately bounded</b>: "found" is resolved to the same {@code TARGET_TABLES} set
 * {@code loot.ResearchNoteLootInjector}/{@link RuneLootInjector} already use for "dungeon chests and
 * such" -- NOT every loot table in the game. Appending a custom function to literally every table
 * (block drops, entity drops, gift tables, every other mod's own tables) would be a much larger
 * performance/compatibility footprint for a cosmetic rarity feature than this suite's existing
 * precedent for "where interesting loot can be found" already covers; easy to widen later if asked.
 *
 * <p>Appended as a TABLE-level function (not a new pool) via {@link LootTableReflection#withExtraFunction}
 * -- it needs to run against items the table's own EXISTING entries generate (any weapon/tool/armor
 * already listed there), not add a new entry of its own. Per-stack eligibility (`EquipmentCategory.of`)
 * means it's harmless no-op for every non-equipment item those tables also generate (food, blocks,
 * etc.).
 */
public final class PreSocketedGearInjector {

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

    public static void onLootTableLoad(LootTableLoadEvent event) {
        if (!TARGET_TABLES.contains(event.getName())) {
            return;
        }
        event.setTable(LootTableReflection.withExtraFunction(event.getTable(), new PreSocketedGearFunction()));
    }

    /**
     * Index = socket count (1-5), value = exclusive chance of landing on exactly that many sockets.
     * Geometric falloff (~0.3x per extra socket), first-pass placeholder magnitudes, flagged tunable
     * like every other numeric constant in this mod -- total chance of ANY pre-socketing is roughly
     * the sum, ~4.3%.
     */
    private static final double[] CHANCE_BY_SOCKET_COUNT = {0, 0.03, 0.009, 0.0027, 0.0008, 0.00025};

    private record PreSocketedGearFunction() implements LootItemFunction {
        @Override
        public ItemStack apply(ItemStack stack, LootContext context) {
            if (EquipmentCategory.of(stack) == null) {
                return stack;
            }
            int socketCount = rollSocketCount(context.getRandom());
            if (socketCount <= 0) {
                return stack;
            }
            SocketData data = SocketData.EMPTY;
            for (int i = 0; i < socketCount; i++) {
                data = data.withNewEmptySocket();
            }
            ItemStack result = stack.copy();
            result.set(ModItems.SOCKET_DATA.get(), data);
            SocketApplier.recompute(result);
            return result;
        }

        private static int rollSocketCount(RandomSource random) {
            double roll = random.nextDouble();
            double cumulative = 0;
            for (int n = SocketData.MAX_SOCKETS; n >= 1; n--) {
                cumulative += CHANCE_BY_SOCKET_COUNT[n];
                if (roll < cumulative) {
                    return n;
                }
            }
            return 0;
        }

        @Override
        public MapCodec<? extends LootItemFunction> codec() {
            throw new UnsupportedOperationException("PreSocketedGearFunction is never serialized");
        }
    }
}
