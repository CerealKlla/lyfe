package com.github.cerealklla.lyfe.magician;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;

/**
 * Canonical ally/enemy relationship for {@link SpellEffect.TargetList}, extracted from {@code
 * SparkOfLifeProjectile}'s original symmetric hostility check rather than duplicated --
 * alignment-symmetric (explicit user spec, 2026-10-20: both hostile or both non-hostile are
 * allies). A caster with no clear hostility (not an {@link Enemy}) is treated as non-hostile,
 * matching the original projectile-side behavior exactly.
 */
public final class SpellTargeting {

    private SpellTargeting() {
    }

    private static boolean hostile(Entity entity) {
        return entity instanceof Enemy;
    }

    public static boolean isAlly(LivingEntity caster, LivingEntity target) {
        return hostile(caster) == hostile(target);
    }

    public static boolean isEnemy(LivingEntity caster, LivingEntity target) {
        return !isAlly(caster, target);
    }

    public static boolean matches(SpellEffect.TargetList targetList, LivingEntity caster, LivingEntity target) {
        return switch (targetList) {
            case ALLY -> isAlly(caster, target);
            case ENEMY -> isEnemy(caster, target);
        };
    }
}
