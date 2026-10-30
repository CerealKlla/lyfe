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
 * <p><b>Scope, originally bounded, widened 2026-10-30</b>: "found" was first resolved to the same
 * {@code TARGET_TABLES} set {@code loot.ResearchNoteLootInjector}/{@link RuneLootInjector} already use
 * for "dungeon chests and such" -- NOT every loot table in the game. A real report surfaced the gap
 * this left: a Zombified Piglin's starting golden sword is vanilla *spawn equipment*, dropped directly
 * via {@code LivingEntity#dropEquipment} on death, never through any loot table at all -- so every
 * such drop had a hard, guaranteed 0% chance of sockets, not just bad luck. Explicit user request:
 * "any mob that drops gear should have the same odds of that gear being socketed." {@link
 * MobEquipmentSocketListener} now applies this exact same roll (see {@link #maybeApplySockets}) to
 * {@code LivingDropsEvent}'s own drops, covering that path too. Chest loot tables stay on the original
 * {@code TARGET_TABLES} allow-list (unchanged) rather than widening to literally every loot table in
 * the game -- same performance/compatibility reasoning as before, just no longer leaving mob
 * equipment uncovered entirely.
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
     * the sum, ~4.3%. Shared by both trigger points (chest loot tables and, since 2026-10-30, natural
     * mob equipment drops via {@link MobEquipmentSocketListener}) -- one odds table, not two.
     */
    private static final double[] CHANCE_BY_SOCKET_COUNT = {0, 0.03, 0.009, 0.0027, 0.0008, 0.00025};

    /**
     * {@code stack} unchanged if it isn't a socketable weapon/tool/armor item, or rolls into zero
     * sockets; otherwise a fresh copy with {@code socketCount} empty (unfilled) sockets applied.
     * The single roll implementation both the chest-loot-table function below and {@link
     * MobEquipmentSocketListener}'s mob-equipment-drop hook call, so "same odds" (explicit user
     * spec) is enforced by sharing code, not by keeping two constants in sync by hand.
     */
    static ItemStack maybeApplySockets(ItemStack stack, RandomSource random) {
        if (EquipmentCategory.of(stack) == null) {
            return stack;
        }
        int socketCount = rollSocketCount(random);
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

    private record PreSocketedGearFunction() implements LootItemFunction {
        @Override
        public ItemStack apply(ItemStack stack, LootContext context) {
            return maybeApplySockets(stack, context.getRandom());
        }

        @Override
        public MapCodec<? extends LootItemFunction> codec() {
            throw new UnsupportedOperationException("PreSocketedGearFunction is never serialized");
        }
    }
}
