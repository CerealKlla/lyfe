package com.github.cerealklla.lyfe.magician;

import com.github.cerealklla.lyfe.api.Lyfe;
import com.github.cerealklla.lyfe.craft.NatureStaffLadder;
import com.github.cerealklla.lyfe.durability.UnbreakableConstants;
import com.github.cerealklla.lyfe.skill.Skills;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Weapon;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * Bo (design doc Section 24: "Same as swordsman or axeman, when using a staff weapon for melee
 * combat. Grants XP upon durability loss.") -- a genuinely different trigger shape than {@code
 * combat.CombatSkillListener}'s own flat per-hit XP: this reads the Nature Staff's real {@code
 * Weapon#itemDamagePerAttack()} (the actual durability this swing just cost) and grants that many
 * XP, 1:1 -- the same "XP cost mirrors real durability spent" convention {@code
 * socket.SocketeerConstants} already established for the Die Grinder/Pry Bar, rather than
 * {@code CombatSkillListener}'s flat-rate-plus-anti-farming-multiplier shape. No mob-damage
 * multiplier here (unlike CombatSkillListener) -- durability loss is the same real cost regardless
 * of who's hit, so there's no cheaper PvP-duel exploit to guard against the way a flat per-hit rate
 * has.
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
        ItemStack weapon = attacker.getMainHandItem();
        // Same real bug CombatSkillListener's own doc describes: a "broken" weapon (durability
        // clamped at maxDamage - 1 by Unbreakable Equipment, never truly destroyed) keeps landing
        // hits with zero real durability effect, so it shouldn't grant XP either.
        if (UnbreakableConstants.isAtZero(weapon)) {
            return;
        }
        if (NatureStaffLadder.tierOfItem(BuiltInRegistries.ITEM.getKey(weapon.getItem())) < 0) {
            return;
        }
        Weapon weaponData = weapon.get(DataComponents.WEAPON);
        if (weaponData == null) {
            return;
        }
        Lyfe.addXp(attacker, Skills.BO_ID, weaponData.itemDamagePerAttack());
    }
}
