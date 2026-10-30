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
            (self, caster, target, magnitude, directCause) -> TaggedEffectDamage.receiveHeal(target, self.combinedTags(), HEAL_AMOUNT));

    /**
     * Real bug fix, 2026-10-30 -- live report: casting Spark of Life was both damaging the caster's
     * own Nature Staff and granting Bo XP (a melee-only skill), and once that staff's durability hit
     * 0, spell damage stopped landing at all. Root cause: this used to build the damage source as
     * {@code target.damageSources().indirectMagic(caster, caster)} -- the CASTER as both the direct
     * and causing entity. Every "was this a genuine melee hit" check elsewhere in this codebase
     * (e.g. {@code craft.ProficiencyDurabilityListener}'s own Bo-Staff branch) keys off {@code
     * DamageSource#getDirectEntity() == attacker} specifically so a spell hit (an indirect source)
     * reads as NOT melee -- but a same-caster direct entity satisfied that check anyway, so a spell
     * hit silently masqueraded as a melee swing everywhere that pattern is used. Now takes the real
     * projectile ({@code directCause}, see {@code SparkOfLifeProjectile#onHitEntity}'s call site) as
     * the direct entity instead, same shape vanilla's own trident/arrow damage already uses.
     */
    public static final SpellEffect DAMAGE_ENEMY_ORGANIC_LIFE = new SpellEffect(
            "damage_enemy_organic_life", SpellEffect.TargetList.ENEMY,
            Set.of("damage_organic"), Set.of("life"), "damaged",
            (self, caster, target, magnitude, directCause) -> TaggedEffectDamage.receiveDamage(target,
                    target.damageSources().indirectMagic(directCause, caster), self.combinedTags(), magnitude));

    private SpellEffects() {
    }
}
