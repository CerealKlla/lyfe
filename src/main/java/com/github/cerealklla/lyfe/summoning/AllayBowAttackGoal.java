package com.github.cerealklla.lyfe.summoning;

import java.util.EnumSet;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;

/**
 * Self-contained bow-firing behavior for a bound Allay whose Equip (offhand) slot holds a real
 * {@code BowItem} -- 2026-10-24 follow-up to {@link AllaySpellCastGoal} ("fight with whatever's
 * equipped" for a non-spell weapon). Settlemynts' {@code guardhouse.GuardEntity} gets the same
 * behavior for free by implementing vanilla's {@code RangedAttackMob} and reusing {@code
 * RangedBowAttackGoal} -- not an option here, since Lyfe has never subclassed {@code Allay} (see
 * {@link AllayGoalInjectionListener}'s own doc) and a vanilla {@code Allay} instance can't be made
 * to implement a new interface after the fact without a Mixin. This goal instead ports {@code
 * GuardEntity#performRangedAttack}'s real projectile-spawning logic directly, with its own simple
 * keep-at-range movement loop standing in for {@code RangedBowAttackGoal}'s own (confirmed against
 * the real decompiled vanilla source before writing this, same discipline as that earlier work).
 */
public final class AllayBowAttackGoal extends Goal {

    private static final double ATTACK_RADIUS = 10.0;
    private static final double ATTACK_RADIUS_SQR = ATTACK_RADIUS * ATTACK_RADIUS;
    private static final double TOO_CLOSE_RADIUS_SQR = (ATTACK_RADIUS * 0.5) * (ATTACK_RADIUS * 0.5);
    private static final int SHOOT_COOLDOWN_TICKS = 20;

    private final Allay allay;
    private int cooldown;

    public AllayBowAttackGoal(Allay allay) {
        this.allay = allay;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = allay.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void stop() {
        allay.getNavigation().stop();
        cooldown = 0;
        // Clears the "actively engaged" signal AllayCombatBrainSuppressMixin reads -- without this,
        // a dead-but-still-referenced target would leave the Allay's own Brain (gathering, etc.)
        // suppressed forever until the next real combat event re-pushes a target.
        allay.setTarget(null);
    }

    @Override
    public void tick() {
        LivingEntity target = allay.getTarget();
        if (target == null) {
            return;
        }
        double distanceSqr = allay.distanceToSqr(target);
        boolean lineOfSight = allay.hasLineOfSight(target);
        allay.getLookControl().setLookAt(target, 30.0F, 30.0F);

        if (distanceSqr > ATTACK_RADIUS_SQR || !lineOfSight) {
            allay.getNavigation().moveTo(target, 1.0);
        } else if (distanceSqr < TOO_CLOSE_RADIUS_SQR) {
            // Back away rather than closing in further -- same strafe-based retreat vanilla's own
            // RangedBowAttackGoal uses (confirmed against the real decompiled source), simplified
            // to a plain straight-backward strafe rather than its clockwise/counter-clockwise dance.
            allay.getNavigation().stop();
            allay.getMoveControl().strafe(-0.5F, 0.0F);
        } else {
            allay.getNavigation().stop();
        }

        if (cooldown > 0) {
            cooldown--;
            return;
        }
        if (distanceSqr <= ATTACK_RADIUS_SQR && lineOfSight) {
            shoot(target);
            cooldown = SHOOT_COOLDOWN_TICKS;
        }
    }

    /** Direct port of {@code GuardEntity#performRangedAttack} (confirmed against the real decompiled vanilla source there). */
    private void shoot(LivingEntity target) {
        if (!(allay.level() instanceof ServerLevel level)) {
            return;
        }
        ItemStack bowItem = allay.getItemInHand(InteractionHand.OFF_HAND);
        ItemStack projectileStack = allay.getProjectile(bowItem);
        AbstractArrow arrow = ProjectileUtil.getMobArrow(allay, projectileStack, 1.0F, bowItem);
        double xd = target.getX() - allay.getX();
        double yd = target.getY(0.3333333333333333) - arrow.getY();
        double zd = target.getZ() - allay.getZ();
        double distanceToTarget = Math.sqrt(xd * xd + zd * zd);
        Projectile.spawnProjectileUsingShoot(
                arrow, level, projectileStack, xd, yd + distanceToTarget * 0.2F, zd, 1.6F,
                14 - level.getDifficulty().getId() * 4);
        allay.playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (allay.getRandom().nextFloat() * 0.4F + 0.8F));
    }
}
