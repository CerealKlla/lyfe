package com.github.cerealklla.lyfe.summoning;

import java.util.EnumSet;

import com.github.cerealklla.lyfe.registration.ModAttachments;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.allay.Allay;

/**
 * Target-selector counterpart to {@link AllaySpellCastGoal}'s own owner-combat-state resolution,
 * for a bound Allay equipped with a plain (non-spell) weapon -- "fight with whatever's equipped"
 * (explicit follow-up request, 2026-10-24). Same source of truth as the spell goal: an Allay has no
 * native combat-targeting of its own at all (unlike Settlemynts' {@code GuardEntity}, which has a
 * dedicated sight goal), so a target is resolved directly off the *owning player's* own vanilla
 * {@link LivingEntity#getLastHurtMob()}/{@link LivingEntity#getLastHurtByMob()} (100-tick recency
 * window), then written to {@link Allay#setTarget} so vanilla's own {@code MeleeAttackGoal}/{@link
 * AllayBowAttackGoal} can act on it exactly like any ordinary targetSelector goal would. Added/
 * removed by {@link AllayGoalInjectionListener} alongside the matching goalSelector entry.
 */
public final class AllayOwnerCombatTargetGoal extends Goal {

    private static final int RECENT_COMBAT_TICKS = 100; // 5 seconds, matches AllaySpellCastGoal.

    private final Allay allay;
    private LivingEntity target;

    public AllayOwnerCombatTargetGoal(Allay allay) {
        this.allay = allay;
        setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        target = resolveAttackTarget();
        return target != null;
    }

    @Override
    public boolean canContinueToUse() {
        return target != null && target.isAlive();
    }

    @Override
    public void start() {
        allay.setTarget(target);
    }

    @Override
    public void stop() {
        allay.setTarget(null);
        target = null;
    }

    private ServerPlayer owner() {
        if (!(allay.level() instanceof ServerLevel level)) {
            return null;
        }
        var ownerData = allay.getExistingData(ModAttachments.SUMMONED_ALLAY_OWNER);
        if (ownerData.isEmpty()) {
            return null;
        }
        return level.getServer().getPlayerList().getPlayer(ownerData.get().playerId());
    }

    private LivingEntity resolveAttackTarget() {
        ServerPlayer player = owner();
        if (player == null) {
            return null;
        }
        // See AllaySpellCastGoal#resolveAttackTarget's own doc for the real bug this avoids --
        // the timestamp is stamped from the player entity's own tickCount, not world game time.
        int now = player.tickCount;
        LivingEntity attacking = player.getLastHurtMob();
        if (attacking != null && attacking.isAlive() && now - player.getLastHurtMobTimestamp() <= RECENT_COMBAT_TICKS) {
            return attacking;
        }
        LivingEntity attacker = player.getLastHurtByMob();
        if (attacker != null && attacker.isAlive() && now - player.getLastHurtByMobTimestamp() <= RECENT_COMBAT_TICKS) {
            return attacker;
        }
        return null;
    }
}
