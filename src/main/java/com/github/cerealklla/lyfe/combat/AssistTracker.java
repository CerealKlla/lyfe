package com.github.cerealklla.lyfe.combat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.github.cerealklla.lyfe.registration.ModAttachments;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
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
 * marker).
 *
 * <p><b>Widened from player-only to any {@code LivingEntity} attacker, 2026-10-23</b> (explicit
 * user spec: loot-pool/gold-find bonuses "should extend further to include NPCs and Minions/
 * Mercs") -- {@code event.getSource().getEntity() instanceof LivingEntity} now covers Settlemynts'
 * Guards, Lyfe's own bound Allay companions, and any future summoned minion/mercenary entity with
 * zero further code changes, since none of those are hardcoded here. This also naturally excludes
 * damage-over-time ticks (burning, wither, poison) for free -- vanilla's own environmental/status-
 * tick damage sources carry no live attacking entity at the instant they're applied, so there's
 * nothing to special-case. {@code victim instanceof Player} still excludes PvP from generating any
 * credit at all. Confirmed safe for every existing consumer (Huntsman, Myssions' quest-kill
 * bridge): both resolve a credited id via {@code ServerLevel#getPlayerList().getPlayer(id)} and
 * already null-check before acting, so a non-player id picked up by this widening is silently
 * skipped by them rather than mishandled.
 */
public final class AssistTracker {

    public static final int ASSIST_DURATION_TICKS = 15 * 20;

    @SubscribeEvent
    public void onDamagePost(LivingDamageEvent.Post event) {
        if (event.getHealthDamage() <= 0) {
            return;
        }
        LivingEntity victim = event.getEntity();
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker) || victim == attacker || victim instanceof Player) {
            return;
        }
        long expiry = victim.level().getGameTime() + ASSIST_DURATION_TICKS;
        victim.getData(ModAttachments.MOB_ASSIST_MARKERS).markAssist(attacker.getUUID(), expiry);
    }

    /**
     * Every attacker id (player or not) credited toward killing {@code deadMob} right now (a
     * non-expired assist marker). Kept this name for every existing player-centric call site
     * (Huntsman, Myssions) -- see this class's own doc for why a non-player id mixed into this set
     * is safe for them.
     */
    public static Set<UUID> creditedPlayers(LivingEntity deadMob) {
        return deadMob.getData(ModAttachments.MOB_ASSIST_MARKERS).creditedPlayers(deadMob.level().getGameTime());
    }

    /** Every credited attacker resolved to a live entity (player or not), for callers that need the real entity, not just its id -- skips any id that's no longer loaded. */
    public static List<LivingEntity> creditedAttackerEntities(LivingEntity deadMob) {
        List<LivingEntity> result = new ArrayList<>();
        if (!(deadMob.level() instanceof ServerLevel level)) {
            return result;
        }
        for (UUID id : creditedPlayers(deadMob)) {
            if (level.getEntity(id) instanceof LivingEntity living) {
                result.add(living);
            }
        }
        return result;
    }

    /**
     * Sums {@code attribute}'s live value across every currently-credited attacker on {@code
     * deadMob} (2026-10-23, explicit user spec: "if multiple people with % magic find kill
     * something, the loot pool should be enhanced by both player's stats"). Used for {@code
     * ModAttributes#MAGIC_FIND}/{@code ModAttributes#GOLD_FIND} loot scaling -- see {@code
     * socket.RuneDropListener}/{@code socket.GemDropListener}/{@code loot.GoldFindDropListener}.
     */
    public static double sumAttribute(LivingEntity deadMob, Holder<Attribute> attribute) {
        double total = 0.0;
        for (LivingEntity attacker : creditedAttackerEntities(deadMob)) {
            var instance = attacker.getAttribute(attribute);
            if (instance != null) {
                total += instance.getValue();
            }
        }
        return total;
    }
}
