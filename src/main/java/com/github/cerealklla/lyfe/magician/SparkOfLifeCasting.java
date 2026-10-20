package com.github.cerealklla.lyfe.magician;

import com.github.cerealklla.lyfe.api.Lyfe;
import com.github.cerealklla.lyfe.registration.ModAttachments;

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
 */
public final class SparkOfLifeCasting {

    private static final float LAUNCH_SPEED = 3.0F; // Matches a fully-drawn bow shot.
    private static final float LAUNCH_INACCURACY = 0.0F; // Perfectly accurate -- a deliberate heal/damage spell, not a spread weapon.

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
        if (caster.getData(ModAttachments.SPELL_COOLDOWNS).isOnCooldown(SpellIds.SPARK_OF_LIFE, now)) {
            return CastResult.ON_COOLDOWN;
        }
        int proficiencyLevel = proficiencyLevel(caster);
        int manaCost = SpellProficiencyCurve.manaCost(proficiencyLevel);
        if (!Lyfe.spendMana(caster, manaCost)) {
            return CastResult.NOT_ENOUGH_MANA;
        }
        int cooldownTicks = SpellProficiencyCurve.cooldownTicks(proficiencyLevel);
        caster.getData(ModAttachments.SPELL_COOLDOWNS).startCooldown(SpellIds.SPARK_OF_LIFE, now, cooldownTicks);

        SparkOfLifeProjectile projectile = new SparkOfLifeProjectile(level, caster);
        projectile.shootFromRotation(caster, caster.getXRot(), caster.getYRot(), 0.0F, LAUNCH_SPEED, LAUNCH_INACCURACY);
        level.addFreshEntity(projectile);
        level.playSound(null, caster.getX(), caster.getY(), caster.getZ(),
                SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 1.0F, 1.4F);
        return CastResult.SUCCESS;
    }
}
