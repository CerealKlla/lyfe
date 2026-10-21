package com.github.cerealklla.lyfe.magician;

import com.github.cerealklla.lyfe.registration.ModAttributes;

import net.minecraft.world.entity.LivingEntity;

/**
 * Casting Speed (design doc, 2026-10-21 user spec): "speed in seconds which must elapse between
 * spells" -- the number of ticks {@link SpellCooldowns}' global gate should hold a caster for after
 * any successful cast, read live off {@link ModAttributes#CASTING_SPEED} rather than a fixed
 * constant, so a future skill/gear/Rune bonus to that Attribute takes effect immediately with no
 * change needed here. Deliberately spell-agnostic (not Spark-of-Life-specific) -- every future
 * spell's own casting code should call this the same way.
 */
public final class CastingSpeed {

    private CastingSpeed() {
    }

    public static int ticksFor(LivingEntity caster) {
        double seconds = caster.getAttributeValue(ModAttributes.CASTING_SPEED);
        return (int) Math.round(Math.max(0.0, seconds) * 20.0);
    }
}
