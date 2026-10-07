package com.github.cerealklla.lyfe.hunger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.github.cerealklla.lyfe.api.Lyfe;
import com.github.cerealklla.lyfe.cook.FoodClassification;
import com.github.cerealklla.lyfe.registration.ModAttachments;
import com.github.cerealklla.lyfe.skill.Skills;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gamerules.GameRules;
import net.neoforged.bus.api.SubscribeEvent;
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
 *
 * <p><b>Cook's old flat eat-time bonus (Section 10.2) is retired, 2026-10-03</b> -- superseded by
 * the real cooking overhaul (Section 19.3, see {@code cook.CookingListener}) the same day: Cook XP
 * now comes only from crafting at a cooking structure, never from eating or a burn-kill. {@code
 * hunger.CookedFoods} is deleted along with that mechanic. Survivalist's own eat-time XP/hunger-gain
 * logic below is unaffected. The one addition here: a {@link FoodClassification} Component's eaten
 * nutrition is forced to a flat 2 (= 1 icon) instead of its real vanilla value, implementing
 * Appendix C's "a raw food item always restores exactly 1 icon" rule, which was never actually
 * wired up before.
 */
public final class HungerListener {

    private static final long SURVIVALIST_XP_PER_POINT = 1;
    private static final int COMPONENT_NUTRITION = 2; // = 1 icon

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
            realFood.setSaturation(0.0F);
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
            // 2026-10-05 fix (real report: "I never seem to drop below 3 food icons") -- confirmed
            // against the decompiled FoodData#tick bytecode: an exhaustion threshold crossing spends
            // real saturationLevel FIRST and only decrements real foodLevel once saturation is
            // already at 0. We only ever pinned foodLevel, never saturation -- real eating still
            // calls vanilla's own FoodData#eat independently of this mod's true-hunger mirror (see
            // onUseItemFinish below), so every time the player ate, real saturation climbed back up
            // and silently absorbed exhaustion for a long stretch afterward, during which `drop`
            // above is always 0 and true hunger simply stops moving -- not a hard floor at any
            // specific number, just real saturation happening to still be positive whenever checked.
            // Pinning saturation to 0 here guarantees every future exhaustion crossing decrements
            // real foodLevel immediately, so the mirrored drop is deterministic regardless of what
            // real eating does to vanilla's own saturation field.
            realFood.setSaturation(0.0F);
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

        // Appendix C's "a raw food item always restores exactly 1 icon, never changes" rule --
        // never actually wired up before 2026-10-03. Crafted food (baked by cook.CookingListener)
        // uses its real, already-overridden nutrition as-is.
        int nutrition = FoodClassification.isComponent(original.getItem()) ? COMPONENT_NUTRITION : food.nutrition();

        PlayerHunger hunger = player.getData(ModAttachments.PLAYER_HUNGER);
        int currentMax = currentMaxHunger(player);
        int before = hunger.getTrueHunger();

        // Mirrors vanilla's FoodConstants#saturationByModifier exactly: saturation gained = nutrition * modifier * 2.
        float saturationGained = nutrition * food.saturation() * 2.0F;
        hunger.eat(nutrition, saturationGained, currentMax);
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
    }

    /** Section 10.1: grows linearly from vanilla's baseline to the design doc's 30-icon/60-point cap as Survivalist levels. */
    public static int currentMaxHunger(ServerPlayer player) {
        return currentMaxHunger(Lyfe.getLevel(player, Skills.SURVIVALIST_ID));
    }

    /** Pure, level-only overload -- used by the Skills screen, which only has a client-side level, not a {@code ServerPlayer}. */
    public static int currentMaxHunger(int level) {
        int growth = HungerConstants.MAX_HUNGER_AT_MAX_LEVEL - HungerConstants.BASE_MAX_HUNGER;
        return HungerConstants.BASE_MAX_HUNGER + growth * level / Skills.MAX_LEVEL;
    }

    /** Live benefit readout for the Skills screen (common.skill.SkillBenefits). */
    public static java.util.List<String> survivalistBenefitLines(int level) {
        return java.util.List.of("True max hunger: " + currentMaxHunger(level));
    }
}
