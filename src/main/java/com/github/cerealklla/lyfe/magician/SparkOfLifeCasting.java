package com.github.cerealklla.lyfe.magician;

import com.github.cerealklla.lyfe.api.Lyfe;
import com.github.cerealklla.lyfe.craft.NatureStaffLadder;
import com.github.cerealklla.lyfe.registration.ModAttachments;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * Shared cast-attempt logic for Spark of Life, used by both {@code NatureStaffItem#use} (a real
 * player, right-click) and Settlemynts' future Nature-Staff-wielding Guard AI goal (an NPC caster)
 * -- see design doc Section 24's "NPCs must be able to cast spells too" requirement. Deliberately
 * one shared method rather than two copies, so mana/cooldown enforcement can never drift between
 * the player and NPC paths.
 *
 * <p>A successful cast is gated by -- and starts -- two independent cooldowns on {@link
 * SpellCooldowns}: Spark of Life's own per-spell cooldown ({@link SpellProficiencyCurve#cooldownTicks})
 * and the spell-agnostic Casting Speed global gate ({@link CastingSpeed#ticksFor}, added
 * 2026-10-21) that will also block casting any *other* future spell for the same duration.
 */
public final class SparkOfLifeCasting {

    // Real request round, 2026-10-20: first "the healing projectile needs to fly 60% the speed it
    // does now" (3.0F -> 1.8F, a fully-drawn bow shot's own speed), then a same-day follow-up: "make
    // it fly slower, the projectile speed can increase by enough to increase the damage by 1 per
    // tier." Reworked from one flat speed into a per-Nature-Staff-Tier ladder: BASE_LAUNCH_SPEED is
    // Tier 1's own speed (slower again than the 1.8F above), and SPEED_INCREMENT_PER_TIER is sized
    // so the existing damage formula (ceil(velocity * BASE_DAMAGE), BASE_DAMAGE=2.0, see
    // SparkOfLifeProjectile's own doc) lands on exactly +1 damage per Tier: 0.5 velocity per tier *
    // 2.0 damage-per-velocity = 1.0 damage per tier. T1=1.2 (3 dmg) through T5=3.2 (7 dmg).
    private static final float BASE_LAUNCH_SPEED = 1.2F;
    private static final float SPEED_INCREMENT_PER_TIER = 0.5F;
    private static final float LAUNCH_INACCURACY = 0.0F; // Perfectly accurate -- a deliberate heal/damage spell, not a spread weapon.

    /**
     * The launch speed for whichever Nature Staff Tier {@code caster} is currently casting with --
     * resolved from their own held mainhand item (the only way a cast can happen at all, for either
     * a player or an NPC caster), rather than threading a new parameter through every call site.
     * Falls back to Tier 1's own speed if the mainhand item somehow isn't resolvable as a Nature
     * Staff at all (shouldn't normally happen -- defensive only).
     */
    private static float launchSpeedFor(LivingEntity caster) {
        Identifier itemId = BuiltInRegistries.ITEM.getKey(caster.getMainHandItem().getItem());
        int tier = NatureStaffLadder.tierOfItem(itemId);
        if (tier < NatureStaffLadder.MIN_TIER) {
            tier = NatureStaffLadder.MIN_TIER;
        }
        return BASE_LAUNCH_SPEED + (tier - NatureStaffLadder.MIN_TIER) * SPEED_INCREMENT_PER_TIER;
    }

    /** Why a cast did or didn't happen -- a player caster uses this for chat feedback; an NPC goal only cares about {@link #SUCCESS} vs. anything else (falls back to melee either way). */
    public enum CastResult {
        SUCCESS, ON_COOLDOWN, NOT_ENOUGH_MANA
    }

    private SparkOfLifeCasting() {
    }

    /** NPCs always read Proficiency as a flat 1 (explicit spec) -- no tracked state exists for them at all. */
    public static int proficiencyLevel(LivingEntity caster) {
        if (caster instanceof ServerPlayer player) {
            int progress = player.getData(ModAttachments.PLAYER_SPELL_PROFICIENCY).progress(SpellIds.SPARK_OF_LIFE);
            return SpellProficiencyCurve.levelForProgress(progress);
        }
        return 1;
    }

    /**
     * Attempts to cast Spark of Life from {@code caster} -- checks cooldown and mana (in that
     * order, so a failed cooldown check never costs mana), spends mana, starts the cooldown, and
     * fires the projectile. Returns {@code false} (no mana spent, no cooldown started) if either
     * check fails; the caller is responsible for user-facing feedback (a chat message for a player,
     * nothing for an NPC goal that just falls back to melee).
     */
    public static CastResult attemptCast(LivingEntity caster) {
        if (!(caster.level() instanceof Level level) || level.isClientSide()) {
            return CastResult.ON_COOLDOWN; // Never actually reached server-side; just a safe default.
        }
        long now = level.getGameTime();
        var cooldowns = caster.getData(ModAttachments.SPELL_COOLDOWNS);
        if (cooldowns.isOnCooldown(SpellIds.SPARK_OF_LIFE, now) || cooldowns.isGloballyOnCooldown(now)) {
            return CastResult.ON_COOLDOWN;
        }
        int proficiencyLevel = proficiencyLevel(caster);
        int manaCost = SpellProficiencyCurve.manaCost(proficiencyLevel);
        if (!Lyfe.spendMana(caster, manaCost)) {
            return CastResult.NOT_ENOUGH_MANA;
        }
        int cooldownTicks = SpellProficiencyCurve.cooldownTicks(proficiencyLevel);
        cooldowns.startCooldown(SpellIds.SPARK_OF_LIFE, now, cooldownTicks);
        cooldowns.startGlobalCooldown(now, CastingSpeed.ticksFor(caster));

        SparkOfLifeProjectile projectile = new SparkOfLifeProjectile(level, caster);
        projectile.shootFromRotation(caster, caster.getXRot(), caster.getYRot(), 0.0F, launchSpeedFor(caster), LAUNCH_INACCURACY);
        level.addFreshEntity(projectile);
        level.playSound(null, caster.getX(), caster.getY(), caster.getZ(),
                SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 1.0F, 1.4F);
        return CastResult.SUCCESS;
    }
}
