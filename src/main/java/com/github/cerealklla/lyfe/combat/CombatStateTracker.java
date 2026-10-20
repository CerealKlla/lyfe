package com.github.cerealklla.lyfe.combat;

import com.github.cerealklla.lyfe.registration.ModAttachments;

import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * Stamps {@link CombatState} on both sides of any real damage event -- the victim (taking damage)
 * and, when there is one, the attacking {@link LivingEntity} (dealing damage) -- see that class's
 * own doc for the "Surviving Combat" mechanic this backs. Deliberately not restricted to
 * {@code ServerPlayer} on either side (unlike {@code combat.AssistTracker}, which only cares about
 * player-credited kills) -- any entity, NPC included, can be "in combat," since the mechanic itself
 * is phrased generically and this is the one place that state is tracked at all.
 *
 * <p>Only real dealt damage counts (mirrors {@code combat.AssistTracker}'s own {@code
 * getHealthDamage() <= 0} guard) -- a blocked/absorbed "hit" shouldn't start or refresh combat
 * state. Healing is never routed through {@link LivingDamageEvent} at all, so "does not include
 * healing another NPC/Player" from the spec holds with no extra guard needed here.
 */
public final class CombatStateTracker {

    @SubscribeEvent
    public void onDamagePost(LivingDamageEvent.Post event) {
        if (event.getHealthDamage() <= 0) {
            return;
        }
        LivingEntity victim = event.getEntity();
        long gameTime = victim.level().getGameTime();
        victim.getData(ModAttachments.COMBAT_STATE).stampCombat(gameTime);
        if (event.getSource().getEntity() instanceof LivingEntity attacker && attacker != victim) {
            attacker.getData(ModAttachments.COMBAT_STATE).stampCombat(gameTime);
        }
    }
}
