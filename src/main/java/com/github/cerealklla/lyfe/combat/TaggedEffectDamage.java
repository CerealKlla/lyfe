package com.github.cerealklla.lyfe.combat;

import java.util.Set;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Centralized heal/damage application for anything tagged with {@link TagEffectivenessTable}
 * entries -- {@code magician.SpellEffect} and {@code socket.RunewordEffect} both route their real
 * heal/damage through here instead of calling vanilla {@code heal()}/{@code hurtServer()} directly,
 * so affinity-based weakness/resistance (and hard blocks via a 0% entry) apply uniformly wherever
 * either system's effect carries a tag. Deliberately a static utility rather than new instance
 * methods injected onto vanilla {@code Entity} via a second Mixin -- same shape as the existing
 * {@code api.Lyfe} static facade.
 *
 * <p>Scope is deliberately narrow: only the spell and Runeword heal/damage call sites route through
 * this. Vanilla melee/fall/drowning/starving/fire/lava, and every other existing direct heal/damage
 * call in this mod (hunger/swim natural regen and starvation/drowning damage) are untouched.
 */
public final class TaggedEffectDamage {

    private TaggedEffectDamage() {
    }

    /**
     * Heals {@code target} by {@code baseAmount} (scaled by the combined tag/affinity multiplier),
     * overflowing past full health into Absorption -- the same behavior Spark of Life's own
     * original heal had, now a universal "heal effect" shared by every caller. Returns whether
     * anything actually happened (fully blocked, or already at max health+absorption, both count
     * as no-op), since callers must not grant progress/XP for a heal that did nothing.
     */
    public static boolean receiveHeal(LivingEntity target, Set<String> tags, float baseAmount) {
        float remaining = baseAmount * TagEffectivenessTable.multiplierFor(tags, target);
        boolean effective = false;

        float missingHealth = target.getMaxHealth() - target.getHealth();
        if (remaining > 0.0F && missingHealth > 0.0F) {
            float toHeal = Math.min(remaining, missingHealth);
            target.heal(toHeal);
            remaining -= toHeal;
            effective = true;
        }
        if (remaining > 0.0F) {
            double maxAbsorption = target.getAttributeValue(Attributes.MAX_ABSORPTION);
            float currentAbsorption = target.getAbsorptionAmount();
            double room = maxAbsorption - currentAbsorption;
            if (room > 0.0) {
                float toAdd = (float) Math.min(room, remaining);
                target.setAbsorptionAmount(currentAbsorption + toAdd);
                effective = true;
            }
        }
        return effective;
    }

    /** Damages {@code target} by {@code baseAmount} (scaled by the combined tag/affinity multiplier) via {@code damageSource}. Returns whether anything actually happened (a 0% multiplier is a no-op). */
    public static boolean receiveDamage(LivingEntity target, DamageSource damageSource, Set<String> tags, float baseAmount) {
        float amount = baseAmount * TagEffectivenessTable.multiplierFor(tags, target);
        if (amount <= 0.0F || !(target.level() instanceof ServerLevel level)) {
            return false;
        }
        target.hurtServer(level, damageSource, amount);
        return true;
    }
}
