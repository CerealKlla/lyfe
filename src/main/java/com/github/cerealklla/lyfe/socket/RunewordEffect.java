package com.github.cerealklla.lyfe.socket;

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
 * @param chance       0.0-1.0 trigger chance where applicable (ignored by always-on types like {@code LOW_HEALTH_DAMAGE_REDUCTION}/periodic ones)
 * @param magnitude    the primary numeric payload -- meaning depends on {@link Type}, documented per case
 * @param radius       blocks, for area-effect types; 0 if not applicable
 * @param durationTicks effect duration/interval in ticks; 0 if not applicable
 * @param secondary    a second numeric payload for types that need one (e.g. health-threshold fraction); 0 if not applicable
 */
public record RunewordEffect(Type type, double chance, double magnitude, double radius, int durationTicks, double secondary) {

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
