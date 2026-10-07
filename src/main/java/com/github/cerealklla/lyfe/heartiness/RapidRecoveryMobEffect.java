package com.github.cerealklla.lyfe.heartiness;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * Heartiness' post-level-25 proc (design doc, 2026-10-03 user request) -- heals a fixed total of
 * {@link HeartinessConstants#RAPID_HEAL_TOTAL_AMOUNT} (4 hearts) spread evenly across the effect's
 * {@link HeartinessConstants#RAPID_HEAL_DURATION_TICKS} (3 seconds), one tick at a time, mirroring
 * the shape of vanilla's own {@code RegenerationMobEffect} (confirmed against the real decompiled
 * source) but with a fixed total instead of a level-scaled per-tick rate -- this effect is always
 * applied with the same duration, never amplified.
 */
public final class RapidRecoveryMobEffect extends MobEffect {

    public RapidRecoveryMobEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity mob, int amplification) {
        float perTick = HeartinessConstants.RAPID_HEAL_TOTAL_AMOUNT / HeartinessConstants.RAPID_HEAL_DURATION_TICKS;
        if (mob.getHealth() < mob.getMaxHealth()) {
            mob.heal(perTick);
        }
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int tickCount, int amplification) {
        return true;
    }
}
