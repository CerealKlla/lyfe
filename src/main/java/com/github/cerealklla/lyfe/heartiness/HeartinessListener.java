package com.github.cerealklla.lyfe.heartiness;

import com.github.cerealklla.lyfe.LyfeMod;
import com.github.cerealklla.lyfe.api.Lyfe;
import com.github.cerealklla.lyfe.registration.ModAttachments;
import com.github.cerealklla.lyfe.registration.ModMobEffects;
import com.github.cerealklla.lyfe.skill.Skills;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Heartiness (design doc, 2026-10-03 user request, revised the same day after playtesting): bonus
 * max health growing to {@link HeartinessConstants#MAX_HEALTH_MULTIPLIER}x vanilla's base by max
 * level, a post-level-25 chance to proc {@link ModMobEffects#RAPID_RECOVERY} after taking damage,
 * and XP on health recovered from *any* damage -- recovering mob-caused damage specifically grants
 * {@link HeartinessConstants#MOB_DAMAGE_XP_MULTIPLIER}x the base rate. See {@link PlayerHeartiness}
 * for how "which portion of my current missing health came from a mob" is tracked; recovering
 * non-mob-caused missing health is always paid out first (e.g. fall damage heals before a mob hit
 * does), matching the original spec's "reward [mob-damage] xp last" framing even though both
 * sources grant something now.
 */
public final class HeartinessListener {

    private static final Identifier MAX_HEALTH_MODIFIER_ID =
            Identifier.fromNamespaceAndPath(LyfeMod.MODID, "heartiness_max_health");

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        applyMaxHealthBonus(player);
    }

    // Transient modifier re-applied every tick, same pattern as swim.SwimmerListener#applySwimSpeed
    // -- cheap, and avoids needing a separate "level changed" event to keep it in sync.
    private void applyMaxHealthBonus(ServerPlayer player) {
        AttributeInstance instance = player.getAttribute(Attributes.MAX_HEALTH);
        if (instance == null) {
            return;
        }
        instance.removeModifier(MAX_HEALTH_MODIFIER_ID);
        int level = Lyfe.getLevel(player, Skills.HEARTINESS_ID);
        double bonus = bonusMaxHealth(level);
        if (bonus > 0.0) {
            instance.addTransientModifier(new AttributeModifier(
                    MAX_HEALTH_MODIFIER_ID, bonus, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    /** Pure, level-only -- grows linearly from 0 (level 0) to MAX_HEALTH_MULTIPLIER's full bonus at max level. */
    public static double bonusMaxHealth(int level) {
        double maxBonus = HeartinessConstants.BASE_MAX_HEALTH * (HeartinessConstants.MAX_HEALTH_MULTIPLIER - 1.0F);
        return maxBonus * level / Skills.MAX_LEVEL;
    }

    @SubscribeEvent
    public void onLivingHeal(LivingHealEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getAmount() <= 0) {
            return;
        }
        float totalMissingBefore = player.getMaxHealth() - player.getHealth();
        float effectiveHeal = Math.min(event.getAmount(), totalMissingBefore);
        if (effectiveHeal <= 0) {
            return;
        }

        PlayerHeartiness state = player.getData(ModAttachments.PLAYER_HEARTINESS);
        state.clampTo(totalMissingBefore);
        float nonMobMissingBefore = totalMissingBefore - state.getMobMissingHealth();

        // Non-mob-caused missing health is always repaid first (e.g. fall damage heals before a mob
        // hit does) -- only the portion left over, if any, drains the mob-caused bucket.
        float healedNonMob = Math.min(effectiveHeal, nonMobMissingBefore);
        float healedMob = state.drain(effectiveHeal - healedNonMob);

        long xp = Math.round(
                healedNonMob * HeartinessConstants.XP_PER_HEALTH_POINT
                        + healedMob * HeartinessConstants.XP_PER_HEALTH_POINT * HeartinessConstants.MOB_DAMAGE_XP_MULTIPLIER);
        if (xp > 0) {
            Lyfe.addXp(player, Skills.HEARTINESS_ID, xp);
        }
    }

    @SubscribeEvent
    public void onDamagePost(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (event.getHealthDamage() > 0 && isMobCaused(event.getSource())) {
            PlayerHeartiness state = player.getData(ModAttachments.PLAYER_HEARTINESS);
            state.clampTo(player.getMaxHealth() - player.getHealth());
            state.bankMobDamage(event.getHealthDamage());
        }

        int level = Lyfe.getLevel(player, Skills.HEARTINESS_ID);
        if (level < HeartinessConstants.RAPID_HEAL_MIN_LEVEL) {
            return;
        }
        if (player.level().getRandom().nextDouble() < rapidHealChance(level)) {
            // Extends an already-active Rapid Recovery instead of just taking the longer of the two
            // (standing rule for every custom buff in this mod, 2026-10-03, see rest.BedRestListener's
            // own doc for the Well Rested bug that established it) -- a proc landing while one is
            // already ticking adds its own duration on top, rather than only refreshing if longer.
            MobEffectInstance current = player.getEffect(ModMobEffects.RAPID_RECOVERY);
            int currentDuration = current != null ? current.getDuration() : 0;
            int newDuration = currentDuration + HeartinessConstants.RAPID_HEAL_DURATION_TICKS;
            // amplifier 0, not ambient, particles off (2026-10-03 user request -- "I don't like the
            // bubbles floating off the character"), icon kept on so it's still visible in the HUD.
            player.addEffect(new MobEffectInstance(ModMobEffects.RAPID_RECOVERY, newDuration, 0, false, false, true));
        }
    }

    /** Same "is this a living, non-player attacker" check vanilla's own difficulty scaling uses (confirmed against the decompiled DamageSource#scalesWithDifficulty) -- covers melee and projectiles (arrow source resolves to its shooter), excludes fall/fire/lava/drowning/cactus/PvP. */
    private static boolean isMobCaused(DamageSource source) {
        return source.getEntity() instanceof LivingEntity && !(source.getEntity() instanceof Player);
    }

    /** Pure, level-only -- 0% below RAPID_HEAL_MIN_LEVEL, growing linearly to RAPID_HEAL_MAX_CHANCE at max level. */
    public static double rapidHealChance(int level) {
        if (level < HeartinessConstants.RAPID_HEAL_MIN_LEVEL) {
            return 0.0;
        }
        int span = Skills.MAX_LEVEL - HeartinessConstants.RAPID_HEAL_MIN_LEVEL;
        if (span <= 0) {
            return HeartinessConstants.RAPID_HEAL_MAX_CHANCE;
        }
        double progress = (level - HeartinessConstants.RAPID_HEAL_MIN_LEVEL) / (double) span;
        return HeartinessConstants.RAPID_HEAL_MAX_CHANCE * progress;
    }

    /** Live benefit readout for the Skills screen (common.skill.SkillBenefits). */
    public static java.util.List<String> benefitLines(int level) {
        double bonusHealth = bonusMaxHealth(level);
        double totalHearts = (HeartinessConstants.BASE_MAX_HEALTH + bonusHealth) / 2.0;
        java.util.List<String> lines = new java.util.ArrayList<>();
        lines.add("Max health: " + String.format(java.util.Locale.ROOT, "%.1f", totalHearts) + " hearts");
        if (level >= HeartinessConstants.RAPID_HEAL_MIN_LEVEL) {
            lines.add("Rapid Recovery proc chance: "
                    + String.format(java.util.Locale.ROOT, "%.1f", rapidHealChance(level) * 100.0) + "%");
        } else {
            lines.add("Rapid Recovery unlocks at level " + HeartinessConstants.RAPID_HEAL_MIN_LEVEL);
        }
        return lines;
    }
}
