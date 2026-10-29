package com.github.cerealklla.lyfe.summoning;

import com.github.cerealklla.lyfe.registration.ModAttachments;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.minecraft.world.phys.AABB;

/**
 * Pushes a bound, armed Allay's combat target the instant the real event happens -- the owning
 * player deals damage, or takes it -- instead of any kind of per-tick "was there recent combat"
 * polling (explicit user correction, 2026-10-24: "There should not be a tick, there should be the
 * event player deals damage and player takes damage, both tell the Allay to target the enemy").
 * Both directions funnel through the same real combat event, {@link LivingDamageEvent.Post}
 * (mirrors {@code combat.AssistTracker}'s own real-dealt-damage guard): {@code event.getEntity()}
 * is the victim, {@code event.getSource().getEntity()} is the attacker (when a living one exists).
 *
 * <p>A real, armed Allay within {@link #OWNER_SEARCH_RADIUS} of the involved player is found by a
 * live scan (no persisted player-to-Allay index exists or is needed -- this already mirrors how
 * every other "find this player's stuff nearby" lookup in this suite works) and has its target set
 * directly via {@link Allay#setTarget} -- every attack goal {@link AllayGoalInjectionListener}
 * wires up (vanilla's {@code MeleeAttackGoal}, {@link AllayBowAttackGoal}, {@link
 * AllayCrossbowAttackGoal}) and {@link AllaySpellCastGoal}'s own attack branch already just read
 * that field, so no separate target-selector goal is needed at all. **Deliberately gated on "armed"
 * (non-empty Equip/offhand slot)** -- an unarmed Allay has no attack goal wired up to ever clear a
 * pushed target back to {@code null} again, and {@code summoning.mixin.AllayCombatBrainSuppressMixin}
 * suppresses Brain (gathering, following the owner, dancing) for as long as a target is set, so
 * setting one on an unarmed Allay would silently and permanently break its own Gather behavior.
 */
public final class AllayCombatEventListener {

    private static final double OWNER_SEARCH_RADIUS = 48.0;

    @SubscribeEvent
    public void onDamagePost(LivingDamageEvent.Post event) {
        if (event.getHealthDamage() <= 0) {
            return;
        }
        LivingEntity victim = event.getEntity();
        LivingEntity attacker = event.getSource().getEntity() instanceof LivingEntity living ? living : null;

        if (attacker instanceof ServerPlayer player && victim != player && isValidAllayTarget(victim)) {
            targetOwnedAllays(player, victim);
        }
        if (victim instanceof ServerPlayer player && attacker != null && attacker != player && isValidAllayTarget(attacker)) {
            targetOwnedAllays(player, attacker);
        }
    }

    // Real report, 2026-10-28: a player testing Caryvan's Wagon (its HP/broken-wheel mechanic needs
    // a real LivingEntity, see that mod's own AbstractDamageableDrawnEntity) by hitting their own
    // wagon got their Allay to "help" by also attacking it. Explicit user correction, same day: this
    // should ONLY exclude a Wagon specifically, not redefine what an Allay can target in general
    // (wild animals, NPCs, etc. must still work exactly as before). Caryvan's cart entities are the
    // one real oddity in this suite that extends LivingEntity directly without going through Mob
    // (needed for real HP, see that class's own doc) -- every genuine creature (animal, monster,
    // villager, Settlemynts' own custom NPCs) extends Mob, and a real player is covered separately.
    // Requiring Mob-or-Player excludes exactly that shape (also catches ArmorStand, itself a
    // LivingEntity-direct oddity and equally not a sensible combat target) with no Caryvan
    // dependency needed at all -- everything else behaves identically to before this fix.
    private static boolean isValidAllayTarget(final LivingEntity entity) {
        return entity instanceof Mob || entity instanceof Player;
    }

    private void targetOwnedAllays(ServerPlayer player, LivingEntity target) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        AABB searchBox = player.getBoundingBox().inflate(OWNER_SEARCH_RADIUS);
        for (Allay allay : level.getEntitiesOfClass(Allay.class, searchBox)) {
            var ownerData = allay.getExistingData(ModAttachments.SUMMONED_ALLAY_OWNER);
            if (ownerData.isEmpty() || !ownerData.get().playerId().equals(player.getUUID())) {
                continue;
            }
            if (allay.getItemInHand(InteractionHand.OFF_HAND).isEmpty()) {
                continue;
            }
            allay.setTarget(target);
        }
    }
}
