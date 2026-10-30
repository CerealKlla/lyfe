package com.github.cerealklla.lyfe.magician;

import com.github.cerealklla.lyfe.api.Lyfe;
import com.github.cerealklla.lyfe.durability.UnbreakableConstants;
import com.github.cerealklla.lyfe.skill.Skills;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Weapon;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * Bo (design doc Section 24: "Same as swordsman or axeman, when using a staff weapon for melee
 * combat. Grants XP upon durability loss.") -- a genuinely different trigger shape than {@code
 * combat.CombatSkillListener}'s own flat per-hit XP: this reads the weapon's real {@code
 * Weapon#itemDamagePerAttack()} (the actual durability this swing just cost) and grants that many
 * XP, 1:1 -- the same "XP cost mirrors real durability spent" convention {@code
 * socket.SocketeerConstants} already established for the Die Grinder/Pry Bar, rather than
 * {@code CombatSkillListener}'s flat-rate-plus-anti-farming-multiplier shape. No mob-damage
 * multiplier here (unlike CombatSkillListener) -- durability loss is the same real cost regardless
 * of who's hit, so there's no cheaper PvP-duel exploit to guard against the way a flat per-hit rate
 * has.
 *
 * <p><b>Real design correction, 2026-10-28</b> -- this used to hardcode a check against {@code
 * NatureStaffLadder} directly, which was wrong: "Bo Staff" is a weapon CLASSIFICATION (like Sword/
 * Axe), not one specific item. Nature Staff is merely the first weapon that belongs to it; any
 * future staff (explicit user examples: "Ember Staff," "Charged Staff") grants Bo XP the same way
 * the instant it registers itself via {@link BoStaffClassification#register}. See that class's own
 * doc for the full correction.
 */
public final class BoSkillListener {

    @SubscribeEvent
    public void onDamagePost(LivingDamageEvent.Post event) {
        if (event.getHealthDamage() <= 0) {
            return;
        }
        LivingEntity victim = event.getEntity();
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker) || victim == attacker) {
            return;
        }
        // Melee only, explicit user spec, 2026-10-30: "Bo xp is melee only." A direct hit has
        // getDirectEntity() == getEntity() (both the attacking player); Spark of Life's own hit is
        // an indirect magic source where they now correctly differ (direct = the real projectile,
        // causing = the player -- see SpellEffects' own doc for the matching root fix), same check
        // craft.ProficiencyDurabilityListener's own Bo-Staff branch already uses.
        if (event.getSource().getDirectEntity() != attacker) {
            return;
        }
        ItemStack weapon = attacker.getMainHandItem();
        // Same real bug CombatSkillListener's own doc describes: a "broken" weapon (durability
        // clamped at maxDamage - 1 by Unbreakable Equipment, never truly destroyed) keeps landing
        // hits with zero real durability effect, so it shouldn't grant XP either.
        if (UnbreakableConstants.isAtZero(weapon)) {
            return;
        }
        if (!BoStaffClassification.isBoStaff(weapon.getItem())) {
            return;
        }
        Weapon weaponData = weapon.get(DataComponents.WEAPON);
        if (weaponData == null) {
            return;
        }
        Lyfe.addXp(attacker, Skills.BO_ID, weaponData.itemDamagePerAttack());
    }
}
