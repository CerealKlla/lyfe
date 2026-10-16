package com.github.cerealklla.lyfe.huntsman;

import java.util.Set;
import java.util.UUID;

import com.github.cerealklla.lyfe.api.Lyfe;
import com.github.cerealklla.lyfe.combat.AssistTracker;
import com.github.cerealklla.lyfe.registration.ModAttachments;
import com.github.cerealklla.lyfe.skill.Skills;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Huntsman skill (explicit user spec, 2026-10-16): "maintains an individual xp bar for each
 * type of mob slain... Every level increases a players damage by .5 against that type of enemy
 * every time they deal damage, not counting damage over time damage sources... The Huntsman skill
 * itself gains xp every time a Quarry Knoweldge level increases." See {@link QuarryKnowledgeCurve}
 * for the per-mob-type level math and {@code huntsman.client.ClientHuntsmanBarState}/{@code
 * HuntsmanBarOverlay} for the progress HUD (same visual style as Researcher's own knowledge bars,
 * per explicit request).
 *
 * <p><b>Huntsman XP per Quarry Knowledge level-up is a flagged, tunable placeholder (1)</b> -- the
 * spec says "gains xp" without a number; picked so Huntsman's own flat 5-XP-per-level curve means
 * 5 separate Quarry Knowledge level-ups raise Huntsman by one level, a reasonable default pace.
 *
 * <p>Damage bonus and kill credit both gate on {@code event.getSource().getEntity() instanceof
 * ServerPlayer} and exclude the {@link Player} class as a victim -- same reasoning as {@link
 * AssistTracker}'s own doc: this also naturally excludes damage-over-time ticks (no live attacking
 * entity at the instant a burn/wither/poison tick applies), satisfying "not counting damage over
 * time damage sources" with no extra tag-based special-casing needed.
 */
public final class HuntsmanListener {

    private static final double DAMAGE_BONUS_PER_LEVEL = 0.5;
    private static final long HUNTSMAN_XP_PER_QUARRY_LEVEL = 1;

    // Explicit user request, 2026-10-16: "Hunter should not track Settlement Guards or
    // Villagers/Custom NPCs." Settlemynts' resident/worker NPCs (ResidentVillagerEntity,
    // LumberjackWorkerEntity, FarmerWorkerEntity, MinerWorkerEntity) all extend vanilla Villager
    // (confirmed against their source), so a plain `instanceof Villager` check -- which already
    // has to cover natural-village villagers anyway -- covers all four for free. GuardEntity
    // extends PathfinderMob directly, not Villager, so it needs its own check; matched by registry
    // id rather than a compile dependency on Settlemynts, mirroring this mod's existing
    // Identifier-based soft-reference to Settlemynts entities elsewhere (minimap.ClientGhostMarkerOutlines).
    private static final Identifier SETTLEMYNTS_GUARD = Identifier.fromNamespaceAndPath("settlemynts", "guard");

    private static boolean isExcludedFromHuntsman(LivingEntity victim) {
        if (victim instanceof Villager) {
            return true;
        }
        return SETTLEMYNTS_GUARD.equals(BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType()));
    }

    @SubscribeEvent
    public void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker) || event.getAmount() <= 0) {
            return;
        }
        LivingEntity victim = event.getEntity();
        if (victim == attacker || victim instanceof Player || isExcludedFromHuntsman(victim)) {
            return;
        }
        Identifier mobType = BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType());
        int level = QuarryKnowledgeCurve.levelForKills(mobType, attacker.getData(ModAttachments.PLAYER_HUNTSMAN).kills(mobType));
        if (level > 0) {
            event.setAmount((float) (event.getAmount() + DAMAGE_BONUS_PER_LEVEL * level));
        }
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level) || event.getEntity() instanceof Player) {
            return;
        }
        LivingEntity deadMob = event.getEntity();
        if (isExcludedFromHuntsman(deadMob)) {
            return;
        }
        Identifier mobType = BuiltInRegistries.ENTITY_TYPE.getKey(deadMob.getType());
        Set<UUID> creditedPlayerIds = AssistTracker.creditedPlayers(deadMob);
        for (UUID playerId : creditedPlayerIds) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
            if (player != null) {
                grantKillCredit(player, mobType);
            }
        }
    }

    private void grantKillCredit(ServerPlayer player, Identifier mobType) {
        var data = player.getData(ModAttachments.PLAYER_HUNTSMAN);
        int oldKills = data.kills(mobType);
        int newKills = data.recordKill(mobType);
        PacketDistributor.sendToPlayer(player, new QuarryKnowledgeProgressPayload(mobType, oldKills, newKills));

        int oldLevel = QuarryKnowledgeCurve.levelForKills(mobType, oldKills);
        int newLevel = QuarryKnowledgeCurve.levelForKills(mobType, newKills);
        if (newLevel > oldLevel) {
            Lyfe.addXp(player, Skills.HUNTSMAN_ID, (newLevel - oldLevel) * HUNTSMAN_XP_PER_QUARRY_LEVEL);
        }
    }
}
