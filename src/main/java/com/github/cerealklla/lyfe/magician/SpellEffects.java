package com.github.cerealklla.lyfe.magician;

import java.util.Set;

import com.github.cerealklla.lyfe.combat.TaggedEffectDamage;

/**
 * Every known {@link SpellEffect}, mirrors {@link SpellIds}' "every known X" shape. Both of Spark
 * of Life's effects -- replacing its original hardcoded heal/damage branches one-for-one (see
 * {@link SparkOfLifeProjectile}).
 */
public final class SpellEffects {

    private static final float HEAL_AMOUNT = 2.0F; // 1 heart, same as Spark of Life's original fixed heal.

    public static final SpellEffect HEAL_ORGANIC_ALLY_LIFE = new SpellEffect(
            "heal_organic_ally_life", SpellEffect.TargetList.ALLY,
            Set.of("heal_organic"), Set.of("life"), "healed",
            (self, caster, target, magnitude) -> TaggedEffectDamage.receiveHeal(target, self.combinedTags(), HEAL_AMOUNT));

    public static final SpellEffect DAMAGE_ENEMY_ORGANIC_LIFE = new SpellEffect(
            "damage_enemy_organic_life", SpellEffect.TargetList.ENEMY,
            Set.of("damage_organic"), Set.of("life"), "damaged",
            (self, caster, target, magnitude) -> TaggedEffectDamage.receiveDamage(target,
                    target.damageSources().indirectMagic(caster, caster), self.combinedTags(), magnitude));

    private SpellEffects() {
    }
}
