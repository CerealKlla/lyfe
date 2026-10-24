package com.github.cerealklla.lyfe.loot;

import com.github.cerealklla.lyfe.combat.AssistTracker;
import com.github.cerealklla.lyfe.registration.ModAttributes;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

/**
 * Scales every {@link Items#GOLD_NUGGET} stack in a mob's drops by the summed Gold Find across
 * every assist-credited attacker (2026-10-23, explicit user spec: "we just added % more gold drop
 * as a stat which is supposed to be hooked in... it's called 'gold find'" -- plus "if multiple
 * people with % magic find kill something, the loot pool should be enhanced by both player's
 * stats... extend further to include NPCs and Minions/Mercs", via {@link AssistTracker#sumAttribute}).
 *
 * <p>Deliberately generic -- scans {@link LivingDropsEvent#getDrops()} for Gold Nuggets rather than
 * rolling its own amount, so it scales whatever actually dropped (Yconomics'
 * {@code mob.HostileMobDrops} today, any future source, vanilla or modded, tomorrow) with no
 * dependency on who produced it. Registered at {@link EventPriority#LOW} so it runs after whatever
 * default-priority listener actually created the gold (the same ordering concern {@code
 * zone.NpcMaintenanceTicker} documents elsewhere in this suite) -- Settlemynts' own Guard
 * kill-payment feature then reads the final, already-scaled amount at {@link EventPriority#LOWEST}.
 * Uses probabilistic rounding (floor + a weighted chance to round up by the fractional remainder)
 * rather than a flat floor/round, so a modest bonus on a small base count (e.g. +25% on 2 nuggets)
 * isn't silently eaten by integer truncation most of the time.
 */
public final class GoldFindDropListener {

    private final RandomSource random = RandomSource.create();

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onLivingDrops(LivingDropsEvent event) {
        LivingEntity entity = event.getEntity();
        double goldFind = AssistTracker.sumAttribute(entity, ModAttributes.GOLD_FIND);
        if (goldFind <= 0.0) {
            return;
        }
        double multiplier = 1.0 + goldFind / 100.0;
        for (ItemEntity itemEntity : event.getDrops()) {
            if (!itemEntity.getItem().is(Items.GOLD_NUGGET)) {
                continue;
            }
            int baseCount = itemEntity.getItem().getCount();
            double scaled = baseCount * multiplier;
            int finalCount = (int) Math.floor(scaled);
            double remainder = scaled - finalCount;
            if (remainder > 0.0 && random.nextDouble() < remainder) {
                finalCount++;
            }
            itemEntity.getItem().setCount(Math.max(baseCount, finalCount));
        }
    }
}
