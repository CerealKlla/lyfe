package com.github.cerealklla.lyfe.combat;

import java.util.Map;

import com.github.cerealklla.lyfe.api.Lyfe;
import com.github.cerealklla.lyfe.craft.EquipmentTierLadder;
import com.github.cerealklla.lyfe.skill.SkillId;
import com.github.cerealklla.lyfe.skill.Skills;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * Grants Swordsman/Axeman/Pikeman XP on dealing real melee damage with the matching weapon
 * (2026-10-06, user request) -- the combat-context counterparts the design doc had long flagged as
 * "future, not designed." Deliberately separate from Lumberjack/Miner's own gathering-context XP
 * (gathering.GatheringListener, unchanged) and from Axeman's own Axe gate being a different skill
 * than Lumberjack's pre-existing Axe gate -- see craft.EquipmentTierLadder#governingSkillId's doc.
 *
 * <p>No passive combat bonuses yet (none designed) -- this skill's only real job right now is
 * feeding craft.ProficiencyDurabilityListener's tier-unlock check.
 *
 * <p><b>Anti-farming (2026-10-06, same precedent as heartiness.HeartinessListener's
 * MOB_DAMAGE_XP_MULTIPLIER)</b>: hitting a real mob grants {@link #MOB_DAMAGE_XP_MULTIPLIER}x the
 * base rate -- two players repeatedly hitting each other in a safe duel is a much cheaper, lower-
 * risk XP source than actually fighting mobs, so it's deliberately the worse rate rather than the
 * same one.
 */
public final class CombatSkillListener {

    private static final long XP_PER_HIT = 5;
    private static final long MOB_DAMAGE_XP_MULTIPLIER = 10L;

    private static final Map<EquipmentTierLadder.ToolType, SkillId> WEAPON_SKILL = Map.of(
            EquipmentTierLadder.ToolType.SWORD, Skills.SWORDSMAN_ID,
            EquipmentTierLadder.ToolType.AXE, Skills.AXEMAN_ID,
            EquipmentTierLadder.ToolType.SPEAR, Skills.PIKEMAN_ID
    );

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
        long xp = victim instanceof Player ? XP_PER_HIT : XP_PER_HIT * MOB_DAMAGE_XP_MULTIPLIER;
        for (var entry : WEAPON_SKILL.entrySet()) {
            if (EquipmentTierLadder.tierOfItem(entry.getKey(), weapon.getItem()) >= 0) {
                Lyfe.addXp(attacker, entry.getValue(), xp);
                return;
            }
        }
    }
}
