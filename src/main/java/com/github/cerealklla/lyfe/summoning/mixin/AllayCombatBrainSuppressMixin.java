package com.github.cerealklla.lyfe.summoning.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.animal.allay.Allay;

/**
 * Real bug found live, 2026-10-24: a bound/armed Allay's new combat and spell-casting goals (see
 * {@code AllayOwnerCombatTargetGoal}/{@code AllaySpellCastGoal}/{@code AllayBowAttackGoal}/{@code
 * AllayCrossbowAttackGoal}) correctly set navigation/look-control targets each tick, but visibly
 * never acted on them -- the Allay just wandered/idled instead of chasing or attacking. Root cause,
 * confirmed against the real decompiled {@code Mob#serverAiStep} source: within one tick,
 * {@code targetSelector}/{@code goalSelector} tick FIRST, then {@code navigation.tick()}, then
 * {@code customServerAiStep()} (where {@link Allay}'s own Brain ticks, running its vanilla IDLE
 * activity -- {@code StayCloseToTarget} toward the owner, or a random {@code RunOne} wander/
 * look-target pick), and only THEN {@code moveControl.tick()}/{@code lookControl.tick()} actually
 * consume whatever destination was most recently requested. Since Brain.tick() runs chronologically
 * *after* the classic combat goals' own navigation/look calls but *before* those calls are actually
 * consumed, Brain's own competing request (when its IDLE behaviors fire, which is often) silently
 * overwrites and wins over the combat goal's request every time -- a structural Brain-vs-classic-
 * Goal conflict, not anything specific to melee vs. ranged vs. spell (all three go through the same
 * navigation/lookControl calls and were equally affected).
 *
 * <p>This mixin redirects the one {@code Brain#tick} call inside {@code Allay#customServerAiStep}
 * to skip entirely whenever the Allay is *actively engaged* -- {@code Allay#getTarget() != null}.
 * Reused as a dual-purpose signal: {@code AllayOwnerCombatTargetGoal} sets it for a real attack
 * target (melee/Bow/Crossbow), and {@link com.github.cerealklla.lyfe.summoning.AllaySpellCastGoal}
 * sets it too, for either an attack or a heal-ally engagement (neither goal can ever be present on
 * the same Allay at once, so there's no real attack-target meaning to collide with). **Corrected the
 * same day** from an earlier, too-broad version that suppressed Brain for the Allay's entire armed
 * lifetime (any non-empty Equip slot) -- a real regression caught live: that version also silently
 * stopped a merely-armed-but-not-currently-fighting Allay from ever walking over to fetch a Gather
 * item, since Brain's own {@code GoToWantedItem} IDLE behavior never got to run either. Gating on
 * an actual target instead means Brain (gathering, dancing, following the owner via Gather/
 * LIKED_PLAYER, etc.) runs completely normally whenever nothing is actually being fought or healed,
 * armed or not -- only suppressed for the ticks a combat/spell goal is genuinely doing something.
 * Lyfe's second Mixin ever, after {@code durability.mixin.ItemStackUnbreakableMixin} -- added only
 * after confirming (via the real decompiled source, not a guess) that no event-based alternative can
 * reach "after Brain decided what to do, before moveControl/lookControl consume it" from outside a
 * vanilla, non-subclassed {@code Allay} instance.
 */
@Mixin(Allay.class)
public abstract class AllayCombatBrainSuppressMixin {

    @Redirect(
            method = "customServerAiStep",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/Brain;tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;)V")
    )
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void lyfe$skipBrainTickWhileEngaged(Brain brain, ServerLevel level, LivingEntity body) {
        Allay allay = (Allay) (Object) this;
        if (allay.getTarget() != null) {
            return;
        }
        brain.tick(level, body);
    }
}
