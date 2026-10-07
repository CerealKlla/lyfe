package com.github.cerealklla.lyfe.rest;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * The replacement for vanilla's "everyone in bed -> night skips instantly" mechanic (design doc,
 * 2026-10-03 user request) -- a pure marker status (no attribute modifiers, no per-tick behavior),
 * applied by {@link BedRestListener} with a duration scaled to how long the player actually lay in
 * bed. Deliberately inert beyond existing/counting down: the user's request only specified the
 * status and its duration scaling, not any attached mechanical bonus, so none is invented here.
 */
public final class WellRestedMobEffect extends MobEffect {

    public WellRestedMobEffect(MobEffectCategory category, int color) {
        super(category, color);
    }
}
