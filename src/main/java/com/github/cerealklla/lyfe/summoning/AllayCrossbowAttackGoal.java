package com.github.cerealklla.lyfe.summoning;

import java.util.EnumSet;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;

/**
 * Self-contained crossbow-firing behavior for a bound Allay whose Equip (offhand) slot holds a real
 * {@code CrossbowItem} -- real report, 2026-10-24: a Crossbow fell through {@link
 * AllayGoalInjectionListener}'s "anything that isn't a {@code BowItem} gets a plain melee goal"
 * branch, which is wrong for a ranged weapon the player clearly expected to see fired. Vanilla's own
 * crossbow combat (vanilla's {@code RangedCrossbowAttackGoal}, confirmed against the real decompiled
 * source) needs a mob to implement {@code CrossbowAttackMob} -- not an option on a plain vanilla
 * {@code Allay} instance without a Mixin, same constraint {@link AllayBowAttackGoal} already works
 * around. This ports that goal's real charge/load/shoot state machine directly: {@code
 * startUsingItem}/{@code releaseUsingItem} drive vanilla's own {@code CrossbowItem#onUseTick}
 * (already automatically invoked by {@code LivingEntity}'s normal use-item ticking -- no private
 * loading method needed), and the final shot calls {@code CrossbowItem#performShooting} directly
 * (public) instead of going through the {@code RangedAttackMob} interface. Skips {@code
 * setChargingCrossbow}'s synched "charging" pose entirely -- that field only exists on {@code
 * CrossbowAttackMob} implementors and is purely cosmetic (third-person charging animation), not
 * required for the actual load/fire mechanics.
 */
public final class AllayCrossbowAttackGoal extends Goal {

    private static final double ATTACK_RADIUS = 10.0;
    private static final double ATTACK_RADIUS_SQR = ATTACK_RADIUS * ATTACK_RADIUS;

    private enum CrossbowState { UNCHARGED, CHARGING, CHARGED, READY_TO_ATTACK }

    private final Allay allay;
    private CrossbowState state = CrossbowState.UNCHARGED;
    private int seeTime;
    private int attackDelay;

    public AllayCrossbowAttackGoal(Allay allay) {
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
        if (allay.isUsingItem()) {
            allay.stopUsingItem();
        }
        state = CrossbowState.UNCHARGED;
        seeTime = 0;
        attackDelay = 0;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity target = allay.getTarget();
        if (target == null) {
            return;
        }
        boolean hasLineOfSight = allay.hasLineOfSight(target);
        boolean hadLineOfSight = seeTime > 0;
        if (hasLineOfSight != hadLineOfSight) {
            seeTime = 0;
        }
        seeTime += hasLineOfSight ? 1 : -1;

        double distanceSqr = allay.distanceToSqr(target);
        boolean needsToMove = (distanceSqr > ATTACK_RADIUS_SQR || seeTime < 5) && attackDelay == 0;
        if (needsToMove) {
            allay.getNavigation().moveTo(target, 1.0);
        } else {
            allay.getNavigation().stop();
        }
        allay.getLookControl().setLookAt(target, 30.0F, 30.0F);

        ItemStack weapon = allay.getItemInHand(InteractionHand.OFF_HAND);
        if (!(weapon.getItem() instanceof CrossbowItem crossbow)) {
            return;
        }

        switch (state) {
            case UNCHARGED -> {
                if (!needsToMove) {
                    allay.startUsingItem(InteractionHand.OFF_HAND);
                    state = CrossbowState.CHARGING;
                }
            }
            case CHARGING -> {
                if (!allay.isUsingItem()) {
                    state = CrossbowState.UNCHARGED;
                    return;
                }
                if (allay.getTicksUsingItem() >= CrossbowItem.getChargeDuration(weapon, allay)) {
                    allay.releaseUsingItem();
                    state = CrossbowState.CHARGED;
                    attackDelay = 20 + allay.getRandom().nextInt(20);
                }
            }
            case CHARGED -> {
                attackDelay--;
                if (attackDelay <= 0) {
                    state = CrossbowState.READY_TO_ATTACK;
                }
            }
            case READY_TO_ATTACK -> {
                if (hasLineOfSight && level() instanceof ServerLevel serverLevel && CrossbowItem.isCharged(weapon)) {
                    crossbow.performShooting(serverLevel, allay, InteractionHand.OFF_HAND, weapon, 1.0F, 14 - serverLevel.getDifficulty().getId() * 4, target);
                    state = CrossbowState.UNCHARGED;
                }
            }
        }
    }

    private net.minecraft.world.level.Level level() {
        return allay.level();
    }
}
