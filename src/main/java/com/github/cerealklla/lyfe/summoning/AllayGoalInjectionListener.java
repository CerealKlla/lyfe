package com.github.cerealklla.lyfe.summoning;

import java.lang.reflect.Field;

import com.github.cerealklla.lyfe.api.Lyfe;
import com.github.cerealklla.lyfe.registration.ModAttachments;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * Attaches/detaches {@link AllaySpellCastGoal} on a summoned Allay as its Equip slot changes
 * (design doc Section 27). Lyfe has never subclassed {@code Allay} (unlike Settlemynts'
 * {@code GuardEntity}, a wholly custom {@code Mob}), so there's no constructor to add a goal in --
 * {@code Mob#goalSelector} is {@code protected}, read once via reflection (cached), same idiom
 * Settlemynts' own {@code founding.GhostBlockDisplays} already uses for an identically-protected
 * vanilla field. Only ever touches a goal-selector belonging to a *summoned* Allay (carrying
 * {@link SummonedAllayOwner}) -- a wild Allay is never touched by this at all.
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
        GoalSelector selector = goalSelector(allay);
        selector.removeAllGoals(goal -> goal instanceof AllaySpellCastGoal);
        // The Equip item always ends up in OFFHAND unless it's a real armor piece (see
        // AllayEquipment's own doc) -- and armor has no spell-casting component to check for
        // anyway, so OFFHAND is the only slot that can ever actually hold a spell item.
        ItemStack offhand = allay.getItemInHand(InteractionHand.OFF_HAND);
        if (hasSpell(offhand)) {
            selector.addGoal(2, new AllaySpellCastGoal(allay));
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
