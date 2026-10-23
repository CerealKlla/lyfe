package com.github.cerealklla.lyfe.summoning;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

import com.github.cerealklla.lyfe.api.Lyfe;
import com.github.cerealklla.lyfe.registration.ModAttachments;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;

/**
 * A bound Allay's spell-casting behavior while its Equip slot holds a spell-bearing item (design
 * doc Section 27) -- direct port of Settlemynts' {@code guardhouse.GuardSpellCastGoal} (same
 * priority shape: heal a nearby hurt ally first, else attack, else idle), with one real difference
 * an Allay needs that a Guard doesn't: a Guard acquires its own combat target via a separate sight
 * goal, but an Allay has no combat AI of its own at all, so "a target you are attacking (or has
 * recently damaged the you)" is resolved directly off the *owning player's* own vanilla combat
 * state ({@link LivingEntity#getLastHurtMob()}/{@link LivingEntity#getLastHurtByMob()}, the same
 * general tracking {@code combat.AssistTracker}-style features elsewhere in this suite already
 * read) rather than anything the Allay tracks itself. Added onto a summoned Allay's classic
 * {@code goalSelector}/{@code targetSelector} via reflection by {@link AllayGoalInjectionListener}
 * -- Allay itself runs on the newer Brain AI framework, but classic Goals still tick alongside it
 * with no conflict (confirmed against the real decompiled {@code Mob#tick} source, which ticks both
 * unconditionally), so this needs no Brain-specific integration at all.
 */
public final class AllaySpellCastGoal extends Goal {

    private static final double HEAL_SCAN_RADIUS = 10.0;
    private static final double CAST_RANGE = 15.0;
    private static final double CAST_RANGE_SQR = CAST_RANGE * CAST_RANGE;
    private static final float CRITICAL_HEAL_THRESHOLD = 0.5F;
    private static final float TOP_OFF_HEAL_THRESHOLD = 1.0F;
    private static final int RECENT_COMBAT_TICKS = 100; // 5 seconds.

    private final Allay allay;

    public AllaySpellCastGoal(Allay allay) {
        this.allay = allay;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return findInjuredAlly(CRITICAL_HEAL_THRESHOLD) != null
                || resolveAttackTarget() != null
                || findInjuredAlly(TOP_OFF_HEAL_THRESHOLD) != null;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity criticalAlly = findInjuredAlly(CRITICAL_HEAL_THRESHOLD);
        if (criticalAlly != null) {
            engage(criticalAlly);
            return;
        }
        LivingEntity enemy = resolveAttackTarget();
        if (enemy != null) {
            engage(enemy);
            return;
        }
        LivingEntity topOffAlly = findInjuredAlly(TOP_OFF_HEAL_THRESHOLD);
        if (topOffAlly != null) {
            engage(topOffAlly);
        }
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
        long now = allay.level().getGameTime();
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

    private void engage(LivingEntity target) {
        double distanceSqr = allay.distanceToSqr(target);
        boolean lineOfSight = allay.hasLineOfSight(target);
        if (distanceSqr > CAST_RANGE_SQR || !lineOfSight) {
            allay.getNavigation().moveTo(target, 1.0);
        } else {
            allay.getNavigation().stop();
        }
        double xd = target.getX() - allay.getX();
        double zd = target.getZ() - allay.getZ();
        double horizontalDist = Math.sqrt(xd * xd + zd * zd);
        double aimY = target.getY(0.5) + horizontalDist * 0.2;
        allay.getLookControl().setLookAt(target.getX(), aimY, target.getZ(), 30.0F, 30.0F);
        double yd = aimY - allay.getEyeY();
        allay.setXRot((float) -(Mth.atan2(yd, horizontalDist) * 180.0F / Math.PI));
        allay.setYRot((float) (Mth.atan2(zd, xd) * 180.0F / Math.PI) - 90.0F);
        if (distanceSqr <= CAST_RANGE_SQR && lineOfSight) {
            Lyfe.attemptCastSparkOfLife(allay);
        }
    }

    private LivingEntity findInjuredAlly(float healthFraction) {
        AABB scanBox = allay.getBoundingBox().inflate(HEAL_SCAN_RADIUS);
        List<LivingEntity> candidates = allay.level().getEntitiesOfClass(LivingEntity.class, scanBox,
                e -> e != allay && e.isAlive() && !(e instanceof Enemy) && e.getHealth() < e.getMaxHealth() * healthFraction);
        return candidates.stream().min(Comparator.comparingDouble(e -> e.getHealth() / e.getMaxHealth())).orElse(null);
    }
}
