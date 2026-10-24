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
 * "Gems should spawn with the same chance as runes, with their own appropriate odds at quality"
 * (explicit user spec, 2026-10-17) -- same {@code instanceof Enemy}/{@code LivingDropsEvent} trigger
 * and same {@code BASE_CHANCE}/{@code PILLAGER_CHANCE} values as {@link RuneDropListener}, so "found
 * a Gem" is exactly as common as "found a Rune" was before this. {@link GemType} has no equivalent
 * to {@link RuneWorth}'s Runeword-membership-weighted rarity (Gems aren't a Runeword ingredient at
 * all), so the type itself rolls uniformly across all 6; only {@link GemQuality} gets a real rarity
 * curve, since the spec explicitly singles out quality odds. {@code QUALITY_CHANCE} is a first-pass
 * placeholder shape (common Chipped, rare Perfect), flagged tunable like every other numeric
 * constant in this mod.
 *
 * <p><b>Magic Find scaling, 2026-10-23</b> -- same mechanism and doc as {@link RuneDropListener}'s
 * own; both share the exact same "summed Magic Find across every assist-credited attacker" scale.
 */
public final class GemDropListener {

    private static final double BASE_CHANCE = 0.01;
    private static final double PILLAGER_CHANCE = 0.05;

    /** Index = {@link GemQuality#index()}; sums to 1.0. Chipped most common, Perfect rarest. */
    private static final double[] QUALITY_CHANCE = {0.50, 0.25, 0.15, 0.07, 0.03};

    private final RandomSource random = RandomSource.create();

    @SubscribeEvent
    public void onLivingDrops(LivingDropsEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity instanceof Enemy) || !(entity.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        double baseChance = entity instanceof Pillager ? PILLAGER_CHANCE : BASE_CHANCE;
        double magicFind = com.github.cerealklla.lyfe.combat.AssistTracker.sumAttribute(entity, com.github.cerealklla.lyfe.registration.ModAttributes.MAGIC_FIND);
        double chance = baseChance * (1.0 + magicFind / 100.0);
        if (random.nextDouble() >= chance) {
            return;
        }
        GemType type = GemType.values()[random.nextInt(GemType.values().length)];
        GemQuality quality = rollQuality(random);
        event.getDrops().add(new ItemEntity(serverLevel, entity.getX(), entity.getY(), entity.getZ(), GemItem.create(type, quality)));
    }

    private static GemQuality rollQuality(RandomSource random) {
        double roll = random.nextDouble();
        double cumulative = 0;
        for (GemQuality quality : GemQuality.values()) {
            cumulative += QUALITY_CHANCE[quality.index()];
            if (roll < cumulative) {
                return quality;
            }
        }
        return GemQuality.PERFECT;
    }
}
