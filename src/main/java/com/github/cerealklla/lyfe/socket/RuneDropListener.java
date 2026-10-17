package com.github.cerealklla.lyfe.socket;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

/**
 * "There should be a 1% chance for killing an enemy mob to drop a random rune. 5% from Pillagers"
 * (explicit user spec, 2026-10-18) -- same {@code instanceof Enemy}/{@code LivingDropsEvent} shape
 * {@code yconomics.mob.HostileMobDrops} already uses for its own hostile-mob Gold Nugget drops, so
 * every current and future hostile mob (vanilla or modded) is covered automatically, not a
 * hand-authored mob-id list. The dropped Rune's own identity is {@link RuneWorth#rollRandomRune}'s
 * real worth-weighted roll, not a flat uniform pick.
 */
public final class RuneDropListener {

    private static final double BASE_CHANCE = 0.01;
    private static final double PILLAGER_CHANCE = 0.05;

    private final RandomSource random = RandomSource.create();

    @SubscribeEvent
    public void onLivingDrops(LivingDropsEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity instanceof Enemy) || !(entity.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        double chance = entity instanceof Pillager ? PILLAGER_CHANCE : BASE_CHANCE;
        if (random.nextDouble() >= chance) {
            return;
        }
        RuneId id = RuneWorth.rollRandomRune(random);
        event.getDrops().add(new ItemEntity(serverLevel, entity.getX(), entity.getY(), entity.getZ(), RuneItem.create(id)));
    }
}
