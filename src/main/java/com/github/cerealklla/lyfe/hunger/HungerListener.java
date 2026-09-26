package com.github.cerealklla.lyfe.hunger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.github.cerealklla.lyfe.api.Lyfe;
import com.github.cerealklla.lyfe.registration.ModAttachments;
import com.github.cerealklla.lyfe.skill.Skills;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gamerules.GameRules;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The decoupled-hunger mechanism (design doc Section 10.0): vanilla's real {@link FoodData} keeps
 * running completely untouched (every vanilla system that causes exhaustion -- sprinting, jumping,
 * mining, attacking -- keeps doing so normally), but its real value is pinned to a constant each
 * tick and never trusted for gameplay effects. Instead, the *drop* in the real value each tick is
 * measured (an absolute point loss, never scaled) and mirrored onto {@link PlayerHunger}'s true
 * number, which is what Survivalist actually grows and what every real effect (sprint-lock,
 * regen, starvation) is checked against, using vanilla's own fixed absolute thresholds
 * ({@link HungerConstants}) so a bigger true max is a genuine survivability reward, not a cosmetic
 * rescale. See decisions.md, 2026-09-24, for the full reasoning.
 */
public final class HungerListener {

    private static final long SURVIVALIST_XP_PER_POINT = 1;
    private static final long COOK_XP_PER_BONUS_POINT = 1;
    private static final long COOK_XP_PER_BURN_KILL_POINT = 1;
    private static final double COOK_BONUS_PER_LEVEL = 0.02; // +2%/level, up to +100% at level 50 -- placeholder, tunable

    // Session-only: whether we've pinned a given player's real food level at least once. Skipped on
    // the very first tick seen so vanilla's default (20) isn't misread as a 3-point exhaustion drop.
    private final Set<UUID> initialized = new HashSet<>();

    // Session-only regen/starvation tick timers, one per player -- mirrors FoodData's own private
    // tickTimer field, which isn't persisted across sessions in vanilla either.
    private final Map<UUID, Integer> tickTimers = new HashMap<>();

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        PlayerHunger hunger = player.getData(ModAttachments.PLAYER_HUNGER);
        FoodData realFood = player.getFoodData();
        UUID playerId = player.getUUID();

        if (!initialized.contains(playerId)) {
            realFood.setFoodLevel(HungerConstants.REAL_FOOD_PIN);
            initialized.add(playerId);
            player.syncData(ModAttachments.PLAYER_HUNGER); // First sync so the client sees the true starting value at all.
        } else {
            int drop = HungerConstants.REAL_FOOD_PIN - realFood.getFoodLevel();
            if (drop > 0) {
                hunger.applyRealHungerDrop(drop);
                // Mutating the attachment in place does not auto-sync (confirmed against the
                // decompiled AttachmentHolder source, 2026-09-24) -- without this the client's
                // overlay silently shows a stale value forever after the first tick.
                player.syncData(ModAttachments.PLAYER_HUNGER);
            }
            realFood.setFoodLevel(HungerConstants.REAL_FOOD_PIN);
        }

        enforceSprintLock(player, hunger);
        tickRegenAndStarvation(player, hunger);
    }

    private void enforceSprintLock(ServerPlayer player, PlayerHunger hunger) {
        if (player.isSprinting() && hunger.getTrueHunger() <= HungerConstants.SPRINT_LEVEL) {
            player.setSprinting(false);
        }
    }

    /** A direct port of {@code FoodData#tick}, operating on true hunger/saturation instead of vanilla's real fields. */
    private void tickRegenAndStarvation(ServerPlayer player, PlayerHunger hunger) {
        ServerLevel level = (ServerLevel) player.level();
        int currentMax = currentMaxHunger(player);
        boolean naturalRegen = level.getGameRules().get(GameRules.NATURAL_HEALTH_REGENERATION);
        UUID playerId = player.getUUID();
        int timer = tickTimers.getOrDefault(playerId, 0);

        if (naturalRegen && hunger.getTrueSaturation() > 0.0F && player.isHurt() && hunger.getTrueHunger() >= currentMax) {
            timer++;
            if (timer >= HungerConstants.HEALTH_TICK_COUNT_SATURATED) {
                float spent = Math.min(hunger.getTrueSaturation(), HungerConstants.EXHAUSTION_HEAL);
                player.heal(spent / 6.0F);
                hunger.spendSaturation(spent);
                timer = 0;
            }
        } else if (naturalRegen && hunger.getTrueHunger() >= HungerConstants.HEAL_LEVEL && player.isHurt()) {
            timer++;
            if (timer >= HungerConstants.HEALTH_TICK_COUNT) {
                player.heal(1.0F);
                timer = 0;
            }
        } else if (hunger.getTrueHunger() <= HungerConstants.STARVE_LEVEL) {
            timer++;
            if (timer >= HungerConstants.HEALTH_TICK_COUNT) {
                Difficulty difficulty = level.getDifficulty();
                if (player.getHealth() > 10.0F || difficulty == Difficulty.HARD
                        || player.getHealth() > 1.0F && difficulty == Difficulty.NORMAL) {
                    player.hurtServer(level, player.damageSources().starve(), 1.0F);
                }
                timer = 0;
            }
        } else {
            timer = 0;
        }

        tickTimers.put(playerId, timer);
    }

    /**
     * Blocks eating (not just withholding XP) once true hunger is already at the player's current
     * max -- previously food could be consumed and wasted while full, since decoupling means
     * vanilla's own "can't eat when full" gate no longer means anything (it's checked against the
     * pinned real food level, not true hunger). {@code canAlwaysEat()} foods (golden apples, etc.)
     * are exempt, matching vanilla's own intent for them.
     */
    @SubscribeEvent
    public void onUseItemStart(LivingEntityUseItemEvent.Start event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        FoodProperties food = event.getItem().get(DataComponents.FOOD);
        if (food == null || food.canAlwaysEat()) {
            return;
        }
        PlayerHunger hunger = player.getData(ModAttachments.PLAYER_HUNGER);
        int currentMax = currentMaxHunger(player);
        if (hunger.getTrueHunger() >= currentMax) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onUseItemFinish(LivingEntityUseItemEvent.Finish event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof ServerPlayer player)) {
            return;
        }
        ItemStack original = event.getItem();
        FoodProperties food = original.get(DataComponents.FOOD);
        if (food == null) {
            return;
        }

        Item item = original.getItem();
        boolean cooked = CookedFoods.isCooked(item);
        int cookLevel = Lyfe.getLevel(player, Skills.COOK_ID);
        double cookBonusFraction = cooked ? Math.min(1.0, cookLevel * COOK_BONUS_PER_LEVEL) : 0.0;
        int bonusNutrition = (int) Math.round(food.nutrition() * cookBonusFraction);

        PlayerHunger hunger = player.getData(ModAttachments.PLAYER_HUNGER);
        int currentMax = currentMaxHunger(player);
        int before = hunger.getTrueHunger();

        // Mirrors vanilla's FoodConstants#saturationByModifier exactly: saturation gained = nutrition * modifier * 2.
        float saturationGained = (food.nutrition() + bonusNutrition) * food.saturation() * 2.0F;
        hunger.eat(food.nutrition() + bonusNutrition, saturationGained, currentMax);
        player.syncData(ModAttachments.PLAYER_HUNGER);

        int actualGain = hunger.getTrueHunger() - before;
        if (actualGain <= 0) {
            // Already full -- no XP, matching the anti-farming approach used elsewhere. DEBUG ONLY:
            // said out loud specifically because a silent no-op here is indistinguishable from the
            // whole mechanism being broken -- confirmed via a real "I ate food, saw nothing" report.
            player.sendSystemMessage(Component.literal(
                    "(already at " + before + "/" + currentMax + " true hunger -- no XP)"));
            return;
        }

        Lyfe.addXp(player, Skills.SURVIVALIST_ID, actualGain * SURVIVALIST_XP_PER_POINT);
        if (cooked && bonusNutrition > 0) {
            Lyfe.addXp(player, Skills.COOK_ID, bonusNutrition * COOK_XP_PER_BONUS_POINT);
        }

        debugAnnounce(player, actualGain, cooked, bonusNutrition);
    }

    /**
     * Free cooking, from an animal dying with already-cooked meat in its drops (vanilla's own
     * burnt-drop mechanic: a mob that dies while on fire drops the cooked version) -- grants Cook
     * XP at the moment of death, separate from and in addition to the XP granted later if that meat
     * is actually eaten (design doc Section 10.2).
     *
     * <p>Attribution uses {@link net.minecraft.world.entity.LivingEntity#getLastHurtByPlayer()},
     * not the killing blow's damage source -- this is vanilla's own general "who gets credit for
     * this death" tracking (used for loot/advancement attribution), and it stays set for 100 ticks
     * (5s) after a player's hit regardless of what actually lands the final blow. That's the
     * difference that matters here: igniting an animal with a Fire Aspect weapon, then letting it
     * wander off and die from the burning itself (no player entity on the final damage source),
     * still credits the igniting player as long as death happens within that window. A kill that
     * happens outside the window, or where the player never actually hit the animal (e.g. lit it
     * with flint and steel without landing a hit), won't be credited -- an accepted, documented gap.
     */
    @SubscribeEvent
    public void onLivingDrops(LivingDropsEvent event) {
        if (!(event.getEntity().getLastHurtByPlayer() instanceof ServerPlayer player)) {
            return;
        }

        long totalXp = 0;
        for (ItemEntity drop : event.getDrops()) {
            ItemStack stack = drop.getItem();
            if (!CookedFoods.isCooked(stack.getItem())) {
                continue;
            }
            FoodProperties food = stack.get(DataComponents.FOOD);
            int nutrition = food != null ? food.nutrition() : 1;
            totalXp += (long) nutrition * stack.getCount() * COOK_XP_PER_BURN_KILL_POINT;
        }

        if (totalXp > 0) {
            boolean wasMaxLevel = Lyfe.isMaxLevel(player, Skills.COOK_ID);
            Lyfe.addXp(player, Skills.COOK_ID, totalXp);
            if (!wasMaxLevel) {
                int cookLevel = Lyfe.getLevel(player, Skills.COOK_ID);
                player.sendSystemMessage(Component.literal("+" + totalXp + " Cook XP (Level " + cookLevel + ") — free cooking!"));
            }
        }
    }

    /** DEBUG ONLY -- same stand-in used by GatheringListener until design doc Section 12's real XP feedback exists. */
    private void debugAnnounce(ServerPlayer player, int gained, boolean cooked, int bonusNutrition) {
        boolean showSurvivalist = !Lyfe.isMaxLevel(player, Skills.SURVIVALIST_ID);
        boolean showCook = cooked && bonusNutrition > 0 && !Lyfe.isMaxLevel(player, Skills.COOK_ID);
        if (!showSurvivalist && !showCook) {
            return;
        }

        StringBuilder message = new StringBuilder();
        if (showSurvivalist) {
            int survivalistLevel = Lyfe.getLevel(player, Skills.SURVIVALIST_ID);
            message.append("+").append(gained).append(" Survivalist XP (Level ").append(survivalistLevel).append(")");
        }
        if (showCook) {
            if (!message.isEmpty()) {
                message.append(" | ");
            }
            int cookLevel = Lyfe.getLevel(player, Skills.COOK_ID);
            message.append("+").append(bonusNutrition).append(" Cook XP (Level ").append(cookLevel).append(")");
        }
        player.sendSystemMessage(Component.literal(message.toString()));
    }

    /** Section 10.1: grows linearly from vanilla's baseline to the design doc's 30-icon/60-point cap as Survivalist levels. */
    public static int currentMaxHunger(ServerPlayer player) {
        int level = Lyfe.getLevel(player, Skills.SURVIVALIST_ID);
        int growth = HungerConstants.MAX_HUNGER_AT_MAX_LEVEL - HungerConstants.BASE_MAX_HUNGER;
        return HungerConstants.BASE_MAX_HUNGER + growth * level / Skills.MAX_LEVEL;
    }
}
