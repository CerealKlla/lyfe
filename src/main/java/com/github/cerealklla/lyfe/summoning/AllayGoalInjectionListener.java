package com.github.cerealklla.lyfe.summoning;

import java.lang.reflect.Field;

import com.github.cerealklla.lyfe.api.Lyfe;
import com.github.cerealklla.lyfe.magician.SpellTargetCache;
import com.github.cerealklla.lyfe.registration.ModAttachments;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * Attaches/detaches combat-related goals on a summoned Allay as its Equip slot changes (design doc
 * Section 27, combat follow-up 2026-10-24). Lyfe has never subclassed {@code Allay} (unlike
 * Settlemynts' {@code GuardEntity}, a wholly custom {@code Mob}), so there's no constructor to add a
 * goal in -- {@code Mob#goalSelector} is {@code protected}, read once via reflection (cached), same
 * idiom Settlemynts' own {@code founding.GhostBlockDisplays} already uses for an identically-
 * protected vanilla field. Only ever touches a goal-selector belonging to a *summoned* Allay
 * (carrying {@link SummonedAllayOwner}) -- a wild Allay is never touched by this at all.
 *
 * <p><b>Combat for a non-spell weapon, added 2026-10-24</b> -- a spell-bearing Equip item still gets
 * {@link AllaySpellCastGoal} exactly as before; any other non-empty Equip item instead gets vanilla's
 * own {@link MeleeAttackGoal} or, for a {@code BowItem}/{@code CrossbowItem}, the self-contained
 * {@link AllayBowAttackGoal}/{@link AllayCrossbowAttackGoal} (no vanilla {@code RangedAttackMob}/
 * {@code CrossbowAttackMob} interface available to hang a reused vanilla ranged goal off, since this
 * is a plain vanilla {@code Allay} instance, not a subclass). An empty Equip slot leaves the Allay
 * with no combat behavior at all, same as before this change.
 *
 * <p><b>Target acquisition reworked to be fully event-driven, same day</b> -- there is deliberately
 * no targetSelector goal here at all (the original {@code AllayOwnerCombatTargetGoal} is gone).
 * {@link AllayCombatEventListener} sets {@code Allay#setTarget} directly the instant the owning
 * player deals or takes damage, which every attack goal here already just reads via {@code
 * getTarget()} -- a classic polling/recency-timestamp target-selector goal would have been both an
 * unnecessary tick cost and a real, confirmed source of bugs (an earlier, polling-based version of
 * this compared two unrelated tick counters and almost never found a target at all).
 */
public final class AllayGoalInjectionListener {

    private static volatile Field goalSelectorField;

    @SubscribeEvent
    public void onJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof Allay allay && allay.getExistingData(ModAttachments.SUMMONED_ALLAY_OWNER).isPresent()) {
            reassessSpellGoal(allay);
        }
    }

    /** Called again by {@code AllayBottleMenu#removed()} any time the Equip slot actually changes, so a later re-summon isn't the only time this is re-evaluated. */
    public static void reassessSpellGoal(Allay allay) {
        // The Equip slot changing is exactly "this caster's available spells changed" (today, the
        // only spell source is a mainhand Nature Staff/offhand Focus) -- invalidate the cached
        // canSpellAffectEntity results so a stale "can't affect this target" doesn't linger if a
        // newly-equipped spell item would actually change the answer.
        SpellTargetCache.invalidate(allay);
        GoalSelector goals = goalSelector(allay);
        goals.removeAllGoals(goal -> goal instanceof AllaySpellCastGoal || goal instanceof MeleeAttackGoal
                || goal instanceof AllayBowAttackGoal || goal instanceof AllayCrossbowAttackGoal);

        // The Equip item always ends up in OFFHAND unless it's a real armor piece (see
        // AllayEquipment's own doc) -- and armor has no spell-casting/weapon behavior to check for
        // anyway, so OFFHAND is the only slot that can ever actually hold a spell item or weapon.
        ItemStack offhand = allay.getItemInHand(InteractionHand.OFF_HAND);
        if (hasSpell(offhand)) {
            // Real report, 2026-10-24: "can't melee when spell is on cd or out of mana" -- a bare
            // spell goal left the Allay doing nothing at all once AllaySpellCastGoal#canUse() starts
            // correctly deferring (see that class's own fix, same day). Stacked at a lower priority,
            // same "spell goal first, melee goal as vanilla's own automatic fallback" shape
            // guardhouse.GuardEntity#reassessWeaponGoal already uses -- no manual "is casting
            // available" logic needed here, vanilla's GoalSelector already falls through to the next
            // priority on its own whenever the higher one's canUse()/canContinueToUse() says no.
            goals.addGoal(2, new AllaySpellCastGoal(allay));
            goals.addGoal(3, new MeleeAttackGoal(allay, 1.0, false));
        } else if (!offhand.isEmpty()) {
            if (offhand.getItem() instanceof BowItem) {
                goals.addGoal(2, new AllayBowAttackGoal(allay));
            } else if (offhand.getItem() instanceof net.minecraft.world.item.CrossbowItem) {
                goals.addGoal(2, new AllayCrossbowAttackGoal(allay));
            } else {
                goals.addGoal(2, new MeleeAttackGoal(allay, 1.0, false));
            }
        }
    }

    private static boolean hasSpell(ItemStack stack) {
        return Lyfe.isNatureStaff(stack) || Lyfe.hasOffhandSpell(stack);
    }

    @SuppressWarnings("unchecked")
    private static GoalSelector goalSelector(Mob mob) {
        Field field = goalSelectorField;
        if (field == null) {
            synchronized (AllayGoalInjectionListener.class) {
                field = goalSelectorField;
                if (field == null) {
                    try {
                        field = Mob.class.getDeclaredField("goalSelector");
                        field.setAccessible(true);
                        goalSelectorField = field;
                    } catch (ReflectiveOperationException e) {
                        throw new IllegalStateException("Could not resolve Mob's goalSelector field", e);
                    }
                }
            }
        }
        try {
            return (GoalSelector) field.get(mob);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Could not read Mob's goalSelector field", e);
        }
    }
}
