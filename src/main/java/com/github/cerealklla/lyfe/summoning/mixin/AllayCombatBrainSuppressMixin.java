package com.github.cerealklla.lyfe.summoning.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.github.cerealklla.lyfe.registration.ModAttachments;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
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
 * to skip entirely whenever the Allay is armed (a real {@code SummonedAllayOwner} bound to it, Equip
 * -- offhand -- slot non-empty) -- the exact condition under which {@code AllayGoalInjectionListener}
 * has added a combat or spell goal in the first place. An unarmed bound Allay (empty Equip slot)
 * still runs its Brain completely normally (follow-the-owner via the Gather/LIKED_PLAYER mechanic,
 * dancing, item fetching, etc.), since none of this suite's own goals compete with it in that case.
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
    private void lyfe$skipBrainTickWhileArmed(Brain brain, ServerLevel level, LivingEntity body) {
        Allay allay = (Allay) (Object) this;
        boolean armed = allay.getExistingData(ModAttachments.SUMMONED_ALLAY_OWNER).isPresent()
                && !allay.getItemInHand(InteractionHand.OFF_HAND).isEmpty();
        if (armed) {
            return;
        }
        brain.tick(level, body);
    }
}
