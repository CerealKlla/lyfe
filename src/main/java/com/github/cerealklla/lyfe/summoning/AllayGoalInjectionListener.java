package com.github.cerealklla.lyfe.summoning;

import java.lang.reflect.Field;

import com.github.cerealklla.lyfe.api.Lyfe;
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
 * goal in -- {@code Mob#goalSelector}/{@code targetSelector} are {@code protected}, read once via
 * reflection (cached), same idiom Settlemynts' own {@code founding.GhostBlockDisplays} already uses
 * for an identically-protected vanilla field. Only ever touches a goal-selector belonging to a
 * *summoned* Allay (carrying {@link SummonedAllayOwner}) -- a wild Allay is never touched by this at
 * all.
 *
 * <p><b>Combat for a non-spell weapon, added 2026-10-24</b> -- a spell-bearing Equip item still gets
 * {@link AllaySpellCastGoal} exactly as before; any other non-empty Equip item instead gets {@link
 * AllayOwnerCombatTargetGoal} (targetSelector) paired with either vanilla's own {@link
 * MeleeAttackGoal} or, for a {@code BowItem}, the self-contained {@link AllayBowAttackGoal} (no
 * vanilla {@code RangedAttackMob} interface available to hang a reused {@code RangedBowAttackGoal}
 * off, since this is a plain vanilla {@code Allay} instance, not a subclass -- see that goal's own
 * doc). An empty Equip slot leaves the Allay with no combat behavior at all, same as before this
 * change.
 */
public final class AllayGoalInjectionListener {

    private static volatile Field goalSelectorField;
    private static volatile Field targetSelectorField;

    @SubscribeEvent
    public void onJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof Allay allay && allay.getExistingData(ModAttachments.SUMMONED_ALLAY_OWNER).isPresent()) {
            reassessSpellGoal(allay);
        }
    }

    /** Called again by {@code AllayBottleMenu#removed()} any time the Equip slot actually changes, so a later re-summon isn't the only time this is re-evaluated. */
    public static void reassessSpellGoal(Allay allay) {
        GoalSelector goals = goalSelector(allay);
        GoalSelector targets = targetSelector(allay);
        goals.removeAllGoals(goal -> goal instanceof AllaySpellCastGoal || goal instanceof MeleeAttackGoal
                || goal instanceof AllayBowAttackGoal || goal instanceof AllayCrossbowAttackGoal);
        targets.removeAllGoals(goal -> goal instanceof AllayOwnerCombatTargetGoal);

        // The Equip item always ends up in OFFHAND unless it's a real armor piece (see
        // AllayEquipment's own doc) -- and armor has no spell-casting/weapon behavior to check for
        // anyway, so OFFHAND is the only slot that can ever actually hold a spell item or weapon.
        ItemStack offhand = allay.getItemInHand(InteractionHand.OFF_HAND);
        if (hasSpell(offhand)) {
            goals.addGoal(2, new AllaySpellCastGoal(allay));
        } else if (!offhand.isEmpty()) {
            targets.addGoal(1, new AllayOwnerCombatTargetGoal(allay));
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
        return readSelector(mob, "goalSelector");
    }

    @SuppressWarnings("unchecked")
    private static GoalSelector targetSelector(Mob mob) {
        return readSelector(mob, "targetSelector");
    }

    private static GoalSelector readSelector(Mob mob, String fieldName) {
        Field field = "goalSelector".equals(fieldName) ? goalSelectorField : targetSelectorField;
        if (field == null) {
            synchronized (AllayGoalInjectionListener.class) {
                field = "goalSelector".equals(fieldName) ? goalSelectorField : targetSelectorField;
                if (field == null) {
                    try {
                        field = Mob.class.getDeclaredField(fieldName);
                        field.setAccessible(true);
                        if ("goalSelector".equals(fieldName)) {
                            goalSelectorField = field;
                        } else {
                            targetSelectorField = field;
                        }
                    } catch (ReflectiveOperationException e) {
                        throw new IllegalStateException("Could not resolve Mob's " + fieldName + " field", e);
                    }
                }
            }
        }
        try {
            return (GoalSelector) field.get(mob);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Could not read Mob's " + fieldName + " field", e);
        }
    }
}
