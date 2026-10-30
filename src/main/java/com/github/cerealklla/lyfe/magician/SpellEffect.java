package com.github.cerealklla.lyfe.magician;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * One effect a {@link Spell} can carry -- gated by {@link TargetList} (ally/enemy relationship,
 * checked via {@link SpellTargeting}) and by {@code effectTags}/{@code spellAffinities} against
 * {@code combat.TagEffectivenessTable} (the former drives hard blocks, e.g. {@code heal_organic}
 * vs a non-organic entity; the latter drives weakness/resistance, e.g. {@code life} vs undead --
 * same table, two tag sources). The actual effect logic is {@code action}, an anonymous code block
 * handed {@code this} so it can reach its own tags without a circular static-init reference, plus
 * a per-cast {@code magnitude} the caller supplies (e.g. Spark of Life's tier-scaled fixed damage)
 * -- an effect that doesn't need one (like a fixed-amount heal) simply ignores it.
 */
public final class SpellEffect {

    @FunctionalInterface
    public interface EffectAction {
        /**
         * Returns whether anything actually happened (for progress/feedback gating). {@code
         * directCause} is the real projectile that hit -- NOT the caster -- so a built {@link
         * net.minecraft.world.damagesource.DamageSource}'s own direct entity correctly differs from
         * the caster (see {@link SpellEffects#DAMAGE_ENEMY_ORGANIC_LIFE}'s own doc for the real bug
         * this fixes: every "was this a genuine melee hit" check elsewhere in this codebase keys off
         * {@code DamageSource#getDirectEntity() == attacker}, which a same-caster direct entity would
         * wrongly satisfy for a spell hit too).
         */
        boolean apply(SpellEffect self, LivingEntity caster, LivingEntity target, float magnitude, Entity directCause);
    }

    public enum TargetList {
        ALLY, ENEMY
    }

    private final String id;
    private final TargetList targetList;
    private final Set<String> effectTags;
    private final Set<String> spellAffinities;
    private final String feedbackVerb;
    private final EffectAction action;

    public SpellEffect(String id, TargetList targetList, Set<String> effectTags, Set<String> spellAffinities,
                        String feedbackVerb, EffectAction action) {
        this.id = id;
        this.targetList = targetList;
        this.effectTags = effectTags;
        this.spellAffinities = spellAffinities;
        this.feedbackVerb = feedbackVerb;
        this.action = action;
    }

    public String id() {
        return id;
    }

    public TargetList targetList() {
        return targetList;
    }

    public Set<String> effectTags() {
        return effectTags;
    }

    public Set<String> spellAffinities() {
        return spellAffinities;
    }

    /** E.g. "healed"/"damaged" -- used only for a player caster's chat feedback when this effect is blocked. */
    public String feedbackVerb() {
        return feedbackVerb;
    }

    /** {@code effectTags} and {@code spellAffinities} combined -- both tag sources feed the same {@code combat.TagEffectivenessTable} lookup. */
    public Set<String> combinedTags() {
        if (spellAffinities.isEmpty()) {
            return effectTags;
        }
        if (effectTags.isEmpty()) {
            return spellAffinities;
        }
        Set<String> combined = new HashSet<>(effectTags);
        combined.addAll(spellAffinities);
        return combined;
    }

    /** Applies this effect's code block; returns whether anything actually happened. */
    public boolean apply(LivingEntity caster, LivingEntity target, float magnitude, Entity directCause) {
        return action.apply(this, caster, target, magnitude, directCause);
    }
}
