package com.github.cerealklla.lyfe.socket;

import com.github.cerealklla.lyfe.registration.ModItems;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

/**
 * Applies {@link PreSocketedGearInjector}'s same pre-socketing roll to a mob's own dropped
 * equipment -- explicit user request, 2026-10-30, after a report that 30 Zombified Piglin-dropped
 * golden swords in a row never once had a socket. Root cause: that injector only ever hooked
 * specific chest {@code LootTable}s; a mob's starting spawn equipment (a Zombified Piglin's golden
 * sword, a Piglin Brute's golden axe, etc.) is dropped directly via vanilla's own {@code
 * LivingEntity#dropEquipment}, which never touches a loot table at all -- so every one of those
 * drops had a hard, guaranteed 0% chance, not just bad luck. {@link LivingDropsEvent} fires once
 * per death with every {@link ItemEntity} about to spawn, loot-table-sourced or not (confirmed
 * against the decompiled {@code LivingEntity#captureDrops} wrapping used by this project's other
 * {@code LivingDropsEvent} listeners, e.g. {@link RuneDropListener}), so this covers equipment drops
 * with no new trigger plumbing needed.
 *
 * <p>Skips any stack that already carries {@link ModItems#SOCKET_DATA} -- in practice nothing hits
 * this twice (entity loot tables are outside {@link PreSocketedGearInjector}'s own {@code
 * TARGET_TABLES} allow-list), but it's a cheap, correct guard against ever re-rolling an
 * already-socketed item regardless of how it got that way.
 */
public final class MobEquipmentSocketListener {

    private final RandomSource random = RandomSource.create();

    @SubscribeEvent
    public void onLivingDrops(LivingDropsEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity.level() instanceof net.minecraft.server.level.ServerLevel)) {
            return;
        }
        for (ItemEntity drop : event.getDrops()) {
            ItemStack stack = drop.getItem();
            if (stack.has(ModItems.SOCKET_DATA.get())) {
                continue;
            }
            ItemStack socketed = PreSocketedGearInjector.maybeApplySockets(stack, random);
            if (socketed != stack) {
                drop.setItem(socketed);
            }
        }
    }
}
