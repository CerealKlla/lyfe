package com.github.cerealklla.lyfe.summoning;

import com.github.cerealklla.lyfe.registration.ModAttachments;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.allay.Allay;
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
    // wagon got their Allay to "help" by also attacking it. Two real corrections landed on top of
    // the first attempt (a structural "Mob or Player" guess): the user wanted ONLY Wagon excluded,
    // nothing else redefined -- then asked for a real cross-mod entity-type tag instead of a
    // structural heuristic, so Caryvan (or any future mod) can explicitly opt an entity out without
    // Lyfe needing to guess from its class shape or take a compile dependency. Caryvan tags its own
    // `wagon` entity type `caryvan:non_organic`; this just checks that tag directly.
    private static final TagKey<EntityType<?>> NON_ORGANIC =
            TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath("caryvan", "non_organic"));

    private static boolean isValidAllayTarget(final LivingEntity entity) {
        return !entity.getType().builtInRegistryHolder().is(NON_ORGANIC);
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
