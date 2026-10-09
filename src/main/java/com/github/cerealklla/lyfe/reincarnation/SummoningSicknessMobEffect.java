package com.github.cerealklla.lyfe.reincarnation;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Applied on respawn for 5 minutes (explicit user request, 2026-10-08) -- a pure marker status (no
 * attribute modifiers, no per-tick behavior), same shape as {@code rest.WellRestedMobEffect}. Its
 * only consumer is {@link ReincarnationListener#onLivingDeath}, which checks for its presence to
 * decide whether this death earns any Reincarnation XP at all.
 */
public final class SummoningSicknessMobEffect extends MobEffect {

    public SummoningSicknessMobEffect(MobEffectCategory category, int color) {
        super(category, color);
    }
}
