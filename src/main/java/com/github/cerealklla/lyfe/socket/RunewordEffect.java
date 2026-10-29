package com.github.cerealklla.lyfe.socket;

import java.util.HashSet;
import java.util.Set;

/**
 * One generic, data-driven Runeword proc (explicit user spec: "Runewords should provide very cool
 * benefits, not simple stat improvements... I'd like to avoid two totally different Runewords having
 * very similar effects, with the only difference being %s"). Rather than hand-writing 60 bespoke
 * Java classes (genuinely unbounded engineering effort for a single pass), every Runeword in {@link
 * RunewordCatalog} is one of these ~18 effect primitives parameterized by its own chance/magnitude/
 * radius/duration -- {@link RunewordEffectListener} implements the primitive's real mechanic exactly
 * once, shared by every Runeword that uses it. Distinctness between Runewords comes from varying
 * BOTH the primitive type and its parameters across the 60 entries, not just the numbers.
 *
 * <p>{@code effectTags}/{@code spellAffinities} let a heal/damage-dealing Runeword primitive opt
 * into the same {@code combat.TagEffectivenessTable} rules {@code magician.SpellEffect} uses (hard
 * blocks via a 0% entry, weakness/resistance via an affinity, e.g. a future lightning-proc Runeword
 * tagged {@code life} doing bonus damage to undead) -- see {@link #combinedTags}. Both default to
 * empty via the secondary constructor below, so none of {@link RunewordCatalog}'s existing entries
 * need to change; an empty set multiplies to 100% (no effect on the original behavior).
 *
 * @param chance       0.0-1.0 trigger chance where applicable (ignored by always-on types like {@code LOW_HEALTH_DAMAGE_REDUCTION}/periodic ones)
 * @param magnitude    the primary numeric payload -- meaning depends on {@link Type}, documented per case
 * @param radius       blocks, for area-effect types; 0 if not applicable
 * @param durationTicks effect duration/interval in ticks; 0 if not applicable
 * @param secondary    a second numeric payload for types that need one (e.g. health-threshold fraction); 0 if not applicable
 * @param effectTags   {@code combat.TagEffectivenessTable} tags for this effect's own category; empty if untagged
 * @param spellAffinities {@code combat.TagEffectivenessTable} tags for this effect's magic school/affinity; empty if untagged
 */
public record RunewordEffect(Type type, double chance, double magnitude, double radius, int durationTicks,
                              double secondary, Set<String> effectTags, Set<String> spellAffinities) {

    /** Untagged -- the shape every one of {@link RunewordCatalog}'s existing 60 entries already calls. */
    public RunewordEffect(Type type, double chance, double magnitude, double radius, int durationTicks, double secondary) {
        this(type, chance, magnitude, radius, durationTicks, secondary, Set.of(), Set.of());
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

    public enum Type {
        /** Weapon. On hitting a living entity: {@code chance} to heal every ally within {@code radius} blocks of the target for {@code magnitude} HP. */
        ON_HIT_HEAL_PULSE_AOE,
        /** Weapon. On hitting a living entity: {@code chance} for {@code magnitude} bonus true damage. */
        ON_HIT_DAMAGE_PROC,
        /** Weapon. On hitting a living entity: {@code chance} to apply Slowness amplifier {@code (int) magnitude} for {@code durationTicks}. */
        ON_HIT_SLOW_TARGET,
        /** Weapon. On hitting a living entity: {@code chance} to heal the attacker for {@code magnitude} fraction of the damage dealt. */
        ON_HIT_LIFESTEAL_BURST,
        /** Weapon. On hitting a living entity: {@code chance} to ignite it for {@code durationTicks}. */
        ON_HIT_IGNITE,
        /** Weapon. On hitting a living entity: {@code chance} for Speed amplifier {@code (int) magnitude} on the attacker for {@code durationTicks}. */
        ON_HIT_SPEED_BURST,
        /** Weapon/Tool. On killing a living entity: {@code chance} for an explosion-style burst of {@code magnitude} damage to other hostiles within {@code radius} (no block damage). */
        ON_KILL_EXPLOSION,
        /** Weapon/Tool. On killing a living entity: {@code chance} to grant {@code (long) magnitude} bonus Lyfe XP in whichever combat/gathering skill is contextually relevant. */
        ON_KILL_XP_BURST,
        /** Weapon/Tool. On killing a living entity: {@code chance} to drop {@code (int) magnitude} bonus Gold Nuggets. */
        ON_KILL_GOLD_BURST,
        /** Armor. On taking damage: {@code chance} to reflect {@code magnitude} fraction of it back at the attacker. */
        ON_DAMAGED_REFLECT,
        /** Armor. On taking damage: {@code chance} to heal {@code magnitude} HP. */
        ON_DAMAGED_HEAL,
        /** Armor. On taking damage: {@code chance} for Speed amplifier {@code (int) magnitude} for {@code durationTicks} (a "danger dash"). */
        ON_DAMAGED_SPEED_BURST,
        /** Armor. Always active: {@code magnitude} fraction less damage taken while current health is below {@code secondary} fraction of max. */
        LOW_HEALTH_DAMAGE_REDUCTION,
        /** Armor. Every {@code durationTicks}, heal the wearer {@code magnitude} HP (passive regen). */
        PERIODIC_SELF_REGEN,
        /** Armor. Every {@code durationTicks}, grant every player within {@code radius} blocks (including the wearer) Regeneration amplifier {@code (int) magnitude} for a short burst. */
        PERIODIC_AREA_BUFF_ALLIES,
        /** Tool. On breaking a block: {@code chance} to double the drops. */
        ON_BREAK_BLOCK_BONUS_YIELD,
        /** Tool. On breaking a block: {@code chance} for Haste amplifier {@code (int) magnitude} for {@code durationTicks}. */
        ON_BREAK_BLOCK_HASTE_BURST,
        /** Tool. On breaking a block: {@code chance} to grant {@code (long) magnitude} bonus Lyfe gathering XP. */
        ON_BREAK_BLOCK_XP_BURST
    }
}
