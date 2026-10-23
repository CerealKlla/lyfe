package com.github.cerealklla.lyfe.fishing;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Granted/refreshed by a nearby {@link ShipwreckMarkerEntity} on a successful ping (design doc
 * Section 28, 2026-10-24) -- a pure marker status (no attribute modifiers), same shape as {@code
 * rest.WellRestedMobEffect}. {@link FishermanListener} checks for this directly instead of
 * re-deriving Shipwreck proximity itself at catch time (the real bug that doc fix replaces --
 * {@code StructureManager#getStructureWithPieceAt} never actually returns {@code null}, so the old
 * {@code != null} check was always true).
 */
public final class ShipwreckMobEffect extends MobEffect {

    public ShipwreckMobEffect(MobEffectCategory category, int color) {
        super(category, color);
    }
}
