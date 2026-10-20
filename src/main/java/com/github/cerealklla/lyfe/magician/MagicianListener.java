package com.github.cerealklla.lyfe.magician;

import java.util.List;

import com.github.cerealklla.lyfe.LyfeMod;
import com.github.cerealklla.lyfe.api.Lyfe;
import com.github.cerealklla.lyfe.registration.ModAttachments;
import com.github.cerealklla.lyfe.registration.ModAttributes;
import com.github.cerealklla.lyfe.skill.Skills;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Magician (design doc Section 24): applies the skill's own per-level Max Mana/Mana Regen bonus
 * (+10/+1 per level, exactly mirroring {@code heartiness.HeartinessListener#applyMaxHealthBonus}'s
 * per-tick transient-modifier pattern). Spell Proficiency progress is granted entirely from {@code
 * SparkOfLifeProjectile#onHitEntity} -- healing an ally or damaging an enemy, whichever actually
 * had a real effect (explicit user spec, 2026-10-20: "change Magician to gain XP when a spell heals
 * an ally or damages an enemy. We'll use 'Survived Combat' for something later" -- replacing an
 * earlier version of this class that granted progress on Surviving Combat instead, which turned out
 * to be a real bug on top of being the wrong trigger: it fired for any player who survived combat
 * at all, with no check for whether they even owned a Nature Staff).
 */
public final class MagicianListener {

    private static final Identifier MAX_MANA_MODIFIER_ID = Identifier.fromNamespaceAndPath(LyfeMod.MODID, "magician_max_mana");
    private static final Identifier MANA_REGEN_MODIFIER_ID = Identifier.fromNamespaceAndPath(LyfeMod.MODID, "magician_mana_regen");

    private static final int MAX_MANA_PER_LEVEL = 10;
    private static final int MANA_REGEN_PER_LEVEL = 1;

    // Flagged tunable placeholder, same convention as huntsman.HuntsmanListener's own
    // HUNTSMAN_XP_PER_QUARRY_LEVEL -- 1 Magician XP per Spell Proficiency level gained.
    private static final long MAGICIAN_XP_PER_PROFICIENCY_LEVEL = 1L;

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        applyManaBonus(player);
    }

    private void applyManaBonus(ServerPlayer player) {
        int level = Lyfe.getLevel(player, Skills.MAGICIAN_ID);

        AttributeInstance maxMana = player.getAttribute(ModAttributes.MAX_MANA);
        if (maxMana != null) {
            maxMana.removeModifier(MAX_MANA_MODIFIER_ID);
            if (level > 0) {
                maxMana.addTransientModifier(new AttributeModifier(
                        MAX_MANA_MODIFIER_ID, level * MAX_MANA_PER_LEVEL, AttributeModifier.Operation.ADD_VALUE));
            }
        }

        AttributeInstance manaRegen = player.getAttribute(ModAttributes.MANA_REGEN);
        if (manaRegen != null) {
            manaRegen.removeModifier(MANA_REGEN_MODIFIER_ID);
            if (level > 0) {
                manaRegen.addTransientModifier(new AttributeModifier(
                        MANA_REGEN_MODIFIER_ID, level * MANA_REGEN_PER_LEVEL, AttributeModifier.Operation.ADD_VALUE));
            }
        }
    }

    /**
     * Adds {@code amount} Spell Proficiency progress toward {@code spellId}, granting Magician skill
     * XP for each whole Proficiency level crossed -- same "derive old/new level from old/new
     * cumulative total" shape as {@code huntsman.HuntsmanListener#recordKill}'s own caller. A no-op
     * for anything that isn't a real {@code ServerPlayer} -- NPCs always read Proficiency as a flat
     * 1 (see {@code SparkOfLifeCasting#proficiencyLevel}), never tracked progress, so there's never
     * anything to grant here for them.
     */
    public static void grantProgress(ServerPlayer player, Identifier spellId, int amount) {
        var proficiency = player.getData(ModAttachments.PLAYER_SPELL_PROFICIENCY);
        int oldProgress = proficiency.progress(spellId);
        int oldLevel = SpellProficiencyCurve.levelForProgress(oldProgress);
        int newProgress = proficiency.addProgress(spellId, amount);
        player.syncData(ModAttachments.PLAYER_SPELL_PROFICIENCY);
        int newLevel = SpellProficiencyCurve.levelForProgress(newProgress);
        if (newLevel > oldLevel) {
            Lyfe.addXp(player, Skills.MAGICIAN_ID, (newLevel - oldLevel) * MAGICIAN_XP_PER_PROFICIENCY_LEVEL);
        }
    }

    /** Skills-screen benefit lines (design doc Section 13) -- real current values, never formulas. */
    public static List<String> benefitLines(int level) {
        if (level <= 0) {
            return List.of("No bonus yet.");
        }
        return List.of(
                "+" + (level * MAX_MANA_PER_LEVEL) + " Max Mana",
                "+" + (level * MANA_REGEN_PER_LEVEL) + " Mana Regen per second"
        );
    }
}
