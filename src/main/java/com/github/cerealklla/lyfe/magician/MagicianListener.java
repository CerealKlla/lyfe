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
 * per-tick transient-modifier pattern).
 *
 * <p><b>Magician XP and per-spell Spell Proficiency are two fully independent tracks</b> (explicit
 * user correction, 2026-10-21: "Magician XP gain when hitting any kind of spell" /
 * "Spark of Life Proficiency gain when specifically hitting Spark of Life on a target and that
 * target actually being healed/overhealed" -- replacing an earlier version where Magician XP was
 * only ever granted indirectly, as a side effect of a Spell Proficiency level-up. That made real
 * Magician XP gain vanishingly rare in practice once Proficiency's own per-level cost grew to 125
 * cumulative casts (see {@code SpellProficiencyCurve}), which the player correctly flagged as "not
 * gaining magician xp at all." Magician XP is now granted directly, once per landed spell, via
 * {@link #grantSpellHitXp} -- called from every successful hit branch in
 * {@code SparkOfLifeProjectile#onHitEntity}, heal or damage alike. Spell Proficiency progress
 * ({@link #grantProgress}) is unchanged in shape but now only ever called from the heal/overheal
 * branch -- it no longer grants Magician XP itself at all, since that's this class's own job now.
 */
public final class MagicianListener {

    private static final Identifier MAX_MANA_MODIFIER_ID = Identifier.fromNamespaceAndPath(LyfeMod.MODID, "magician_max_mana");
    private static final Identifier MANA_REGEN_MODIFIER_ID = Identifier.fromNamespaceAndPath(LyfeMod.MODID, "magician_mana_regen");

    private static final int MAX_MANA_PER_LEVEL = 10;
    private static final int MANA_REGEN_PER_LEVEL = 1;

    // Flagged tunable placeholder, same convention as huntsman.HuntsmanListener's own
    // HUNTSMAN_XP_PER_QUARRY_LEVEL -- flat Magician XP per spell that actually lands on a target,
    // heal/overheal or damage alike, decoupled entirely from Spell Proficiency (see class doc).
    private static final long MAGICIAN_XP_PER_SPELL_HIT = 1L;

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
     * Adds {@code amount} Spell Proficiency progress toward {@code spellId} -- purely a per-spell
     * cost/cooldown-reduction track now (see {@link SpellProficiencyCurve}), no longer a Magician XP
     * source itself (see class doc). A no-op for anything that isn't a real {@code ServerPlayer} --
     * NPCs always read Proficiency as a flat 1 (see {@code SparkOfLifeCasting#proficiencyLevel}),
     * never tracked progress, so there's never anything to grant here for them.
     */
    public static void grantProgress(ServerPlayer player, Identifier spellId, int amount) {
        var proficiency = player.getData(ModAttachments.PLAYER_SPELL_PROFICIENCY);
        proficiency.addProgress(spellId, amount);
        player.syncData(ModAttachments.PLAYER_SPELL_PROFICIENCY);
    }

    /** Flat Magician XP for any spell that actually lands on a target -- heal/overheal or damage alike. */
    public static void grantSpellHitXp(ServerPlayer player) {
        Lyfe.addXp(player, Skills.MAGICIAN_ID, MAGICIAN_XP_PER_SPELL_HIT);
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
