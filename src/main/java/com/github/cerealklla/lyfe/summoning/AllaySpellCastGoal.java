package com.github.cerealklla.lyfe.summoning;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

import com.github.cerealklla.lyfe.api.Lyfe;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;

/**
 * A bound Allay's spell-casting behavior while its Equip slot holds a spell-bearing item (design
 * doc Section 27) -- direct port of Settlemynts' {@code guardhouse.GuardSpellCastGoal} (same
 * priority shape: heal a nearby hurt ally first, else attack, else idle). Added onto a summoned
 * Allay's classic {@code goalSelector} via reflection by {@link AllayGoalInjectionListener} --
 * Allay itself runs on the newer Brain AI framework, but classic Goals still tick alongside it with
 * no conflict (confirmed against the real decompiled {@code Mob#tick} source, which ticks both
 * unconditionally), so this needs no Brain-specific integration at all.
 *
 * <p><b>Attack-target resolution reworked to be fully event-driven, 2026-10-24</b> -- a Guard
 * acquires its own combat target via a separate sight goal, but an Allay has no native combat-
 * targeting of its own at all, so this goal's attack branch just reads whatever {@link
 * Allay#getTarget()} already holds, set externally by {@link AllayCombatEventListener} the instant
 * the owning player deals or takes damage (real-time, no tick-based "was there recent combat"
 * polling at all -- explicit correction of an earlier version here that compared stale recency
 * timestamps, confirmed broken, then just slow/indirect even once fixed).
 */
public final class AllaySpellCastGoal extends Goal {

    private static final double HEAL_SCAN_RADIUS = 10.0;
    private static final double CAST_RANGE = 15.0;
    private static final double CAST_RANGE_SQR = CAST_RANGE * CAST_RANGE;
    private static final float CRITICAL_HEAL_THRESHOLD = 0.5F;
    private static final float TOP_OFF_HEAL_THRESHOLD = 1.0F;

    private final Allay allay;

    public AllaySpellCastGoal(Allay allay) {
        this.allay = allay;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    // Real report, 2026-10-24: "if I handed this nature staff to the allay it can't melee when
    // spell is on cd or out of mana because technically it's equipped in it's off-hand" -- confirmed
    // real, two-part bug. Part 1 (this method): unlike guardhouse.GuardSpellCastGoal's own
    // canUseInternal (the reference this goal was ported from), the attack branch here never checked
    // whether a cast was actually possible -- it claimed canUse()=true off target presence alone, so
    // even with a melee fallback goal added (part 2, see AllayGoalInjectionListener), vanilla's own
    // GoalSelector would never fall through to it while any target existed, cast-starved or not.
    @Override
    public boolean canUse() {
        if (findInjuredAlly(CRITICAL_HEAL_THRESHOLD) != null) {
            return true;
        }
        LivingEntity enemy = resolveAttackTarget();
        if (enemy != null) {
            // An enemy is present -- either cast at it, or defer entirely so a stacked melee goal
            // (see AllayGoalInjectionListener) can take over, same as the Guard's own equivalent.
            return Lyfe.canCastSparkOfLife(allay);
        }
        return findInjuredAlly(TOP_OFF_HEAL_THRESHOLD) != null;
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

    @Override
    public void stop() {
        // See AllayOwnerCombatTargetGoal's own doc -- allay.setTarget(...) here doubles as the
        // "actively engaged" signal AllayCombatBrainSuppressMixin reads, not a real attack target
        // when healing. Must be cleared when this goal stops for good (no heal candidate and no live
        // enemy left), or the Allay's Brain (gather-item fetching, etc.) would stay suppressed
        // forever after the last heal/attack.
        //
        // Real report, 2026-10-24, fixed same day as canUse() above: unconditionally nulling here
        // broke the new spell-then-melee goal stack the instant this goal deferred mid-fight (out of
        // mana/on cooldown while an enemy was still alive) -- AllayGoalInjectionListener's stacked
        // MeleeAttackGoal reads the exact same allay.getTarget(), so wiping it right as control
        // handed off meant the fallback goal saw nothing to attack either. Only clear it when the
        // current target isn't a live hostile Enemy -- a heal-target (never an Enemy, see
        // findInjuredAlly's own `!(e instanceof Enemy)` filter) or a dead/already-gone target should
        // still be cleared; a live enemy this goal is mid-fight-but-can't-currently-cast against must
        // survive the handoff so the melee goal actually picks it up.
        LivingEntity target = allay.getTarget();
        boolean handingOffToMelee = target != null && target.isAlive() && target instanceof Enemy;
        if (!handingOffToMelee) {
            allay.setTarget(null);
        }
    }

    /** Whatever {@link AllayCombatEventListener} most recently pushed onto {@link Allay#setTarget} -- see this class's own doc. */
    private LivingEntity resolveAttackTarget() {
        LivingEntity target = allay.getTarget();
        return target != null && target.isAlive() ? target : null;
    }

    private void engage(LivingEntity target) {
        // Doubles as the "actively engaged" signal AllayCombatBrainSuppressMixin reads -- not a real
        // attack target when healing an ally, just reusing the one field nothing else on a bound
        // Allay reads for its own combat purposes (AllayOwnerCombatTargetGoal/this goal are mutually
        // exclusive per Equip item, so there's no real attack-target meaning to collide with).
        allay.setTarget(target);
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
                e -> e != allay && e.isAlive() && isRealAlly(e) && e.getHealth() < e.getMaxHealth() * healthFraction);
        return candidates.stream().min(Comparator.comparingDouble(e -> e.getHealth() / e.getMaxHealth())).orElse(null);
    }

    // Real report, 2026-10-28: "my allay is still trying to heal my wagon." Two real corrections
    // landed on top of a first attempt here: narrowing "ally" down to just the Allay's own owner or
    // another owned Allay was overreach (wild animals, NPCs, etc. must stay healable exactly as
    // before); a follow-up "Mob or Player" structural guess worked but the user asked for a real
    // cross-mod entity-type tag instead, so Caryvan (or any future mod) can explicitly opt an entity
    // out without Lyfe guessing from its class shape. Back to `!(e instanceof Enemy)`, plus checking
    // Caryvan's own `caryvan:non_organic` tag directly -- see AllayCombatEventListener's identical
    // constant/doc for the full reasoning.
    private static final TagKey<EntityType<?>> NON_ORGANIC =
            TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath("caryvan", "non_organic"));

    private boolean isRealAlly(LivingEntity candidate) {
        return !(candidate instanceof Enemy) && !candidate.getType().builtInRegistryHolder().is(NON_ORGANIC);
    }
}
