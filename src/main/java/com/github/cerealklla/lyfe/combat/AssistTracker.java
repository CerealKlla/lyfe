package com.github.cerealklla.lyfe.combat;

import java.util.Set;
import java.util.UUID;

import com.github.cerealklla.lyfe.registration.ModAttachments;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * "Injuring a mob puts an assist marker for the player on that mob, which expires after 15
 * seconds. If the mob dies, every player who has an assist marker on that mob will be given equal
 * credit toward killing it. This is primarily meant to affect quest requirement progress."
 * (explicit user spec, 2026-10-16, General section) -- the shared kill-credit foundation both
 * Myssions' quest-kill tracking ({@code api.Lyfe#getAssistCredit}, via {@code
 * bridge.LyfeAssistBridge} on that side) and Huntsman's own Quarry Knowledge ({@code
 * huntsman.HuntsmanListener}) are built on.
 *
 * <p>Registers a marker only on real dealt damage (mirrors {@code combat.CombatSkillListener}'s
 * own {@code getHealthDamage() <= 0} guard -- a blocked/absorbed "hit" shouldn't start or refresh a
 * marker) from a direct player attacker. Checking {@code event.getSource().getEntity() instanceof
 * ServerPlayer} (the same check {@code CombatSkillListener}/{@code UnbreakableToolListener} already
 * rely on elsewhere in this mod) also naturally excludes damage-over-time ticks (burning, wither,
 * poison) for free -- vanilla's own environmental/status-tick damage sources carry no live
 * attacking entity at the instant they're applied, so there's nothing to special-case here.
 */
public final class AssistTracker {

    public static final int ASSIST_DURATION_TICKS = 15 * 20;

    @SubscribeEvent
    public void onDamagePost(LivingDamageEvent.Post event) {
        if (event.getHealthDamage() <= 0) {
            return;
        }
        LivingEntity victim = event.getEntity();
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker) || victim == attacker || victim instanceof Player) {
            return;
        }
        long expiry = victim.level().getGameTime() + ASSIST_DURATION_TICKS;
        victim.getData(ModAttachments.MOB_ASSIST_MARKERS).markAssist(attacker.getUUID(), expiry);
    }

    /** Every player credited toward killing {@code deadMob} right now (a non-expired assist marker). */
    public static Set<UUID> creditedPlayers(LivingEntity deadMob) {
        return deadMob.getData(ModAttachments.MOB_ASSIST_MARKERS).creditedPlayers(deadMob.level().getGameTime());
    }
}
