package com.github.cerealklla.lyfe.magician;

import java.util.List;

import com.github.cerealklla.lyfe.LyfeMod;
import com.github.cerealklla.lyfe.api.Lyfe;
import com.github.cerealklla.lyfe.registration.ModAttachments;
import com.github.cerealklla.lyfe.registration.ModAttributes;
import com.github.cerealklla.lyfe.skill.Skills;
import com.github.cerealklla.lyfe.xpbar.XpGainPayload;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Magician (design doc Section 24): applies the skill's own per-level Max Mana/Mana Regen bonus
 * (+10/+1 per level, exactly mirroring {@code heartiness.HeartinessListener#applyMaxHealthBonus}'s
 * per-tick transient-modifier pattern).
 *
 * <p><b>Magician's own level is kept in exact lockstep with the sum of the player's per-spell
 * Spell Proficiency levels</b> (explicit user spec, 2026-10-21, verbatim: "Magician: Sum of Spell
 * Proficiencies" -- reverting a same-day detour where Magician XP was granted as a flat amount per
 * landed spell hit, independent of Proficiency entirely. That detour was itself a reaction to a
 * real, correctly-reported symptom -- "Spark of Life max leveled after 8 spells, and Magician
 * seemed to not have" -- but the actual root cause was a curve mismatch, not the wrong trigger:
 * Magician previously reused {@code Skills#huntsmanCurve()} (5 XP per level) fed by a flat 1 XP per
 * Proficiency level *crossed*, so even Spark of Life's entire climb to its own max level (8) could
 * only ever hand Magician 8 total XP -- enough to reach Magician level 1 and no further, no matter
 * how far Proficiency itself had gone. Fixed properly this time: Magician now uses its own flat
 * 1-XP-per-level curve ({@code Skills#magicianCurve()}), and {@link #grantProgress} resyncs
 * Magician's XP to exactly {@link PlayerSpellProficiency#totalProficiencyLevel} every time it
 * changes -- so Magician's level literally *is* that sum, not an approximation of it, both now and
 * once more spells exist to sum over.
 */
public final class MagicianListener {

    private static final Identifier MAX_MANA_MODIFIER_ID = Identifier.fromNamespaceAndPath(LyfeMod.MODID, "magician_max_mana");
    private static final Identifier MANA_REGEN_MODIFIER_ID = Identifier.fromNamespaceAndPath(LyfeMod.MODID, "magician_mana_regen");

    private static final int MAX_MANA_PER_LEVEL = 10;
    private static final int MANA_REGEN_PER_LEVEL = 1;

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
     * Adds {@code amount} Spell Proficiency progress toward {@code spellId} (the per-spell cost/
     * cooldown-reduction track, see {@link SpellProficiencyCurve}), then resyncs Magician's own XP
     * to match the new total across every spell (see class doc). A no-op for anything that isn't a
     * real {@code ServerPlayer} -- NPCs always read Proficiency as a flat 1 (see {@code
     * SparkOfLifeCasting#proficiencyLevel}), never tracked progress, so there's never anything to
     * grant here for them.
     */
    public static void grantProgress(ServerPlayer player, Identifier spellId, int amount) {
        var proficiency = player.getData(ModAttachments.PLAYER_SPELL_PROFICIENCY);
        int oldProgress = proficiency.progress(spellId);
        int newProgress = proficiency.addProgress(spellId, amount);
        player.syncData(ModAttachments.PLAYER_SPELL_PROFICIENCY);
        // Transient per-cast progress HUD (explicit user request, 2026-10-21: "this should look
        // visually like XP gains, same as the popup with Quarry Knowledge and every other similar
        // mechanic we have") -- independent of syncLevelToProficiencySum's own rarer popup below,
        // which only fires once every PROGRESS_PER_LEVEL casts when a whole level is actually
        // crossed. This one fires every single successful cast, same as QuarryKnowledgeProgressPayload.
        PacketDistributor.sendToPlayer(player, new SpellProficiencyProgressPayload(spellId, oldProgress, newProgress));
        syncLevelToProficiencySum(player, proficiency);
    }

    /**
     * Brings Magician's own XP up to exactly {@link PlayerSpellProficiency#totalProficiencyLevel}
     * (1 XP == 1 summed Proficiency level, by construction of {@code Skills#magicianCurve()}).
     * Deliberately bypasses {@link Lyfe#addXp} -- this isn't newly *earned* XP for an action, it's a
     * direct resync to a derived total, and {@code addXp}'s Well Rested doubling would permanently
     * desync Magician's level from the real sum the next time this runs. Still sends the same XP-bar
     * HUD popup {@code addXp} would have, for UX parity with every other skill.
     */
    private static void syncLevelToProficiencySum(ServerPlayer player, PlayerSpellProficiency proficiency) {
        var skills = player.getData(ModAttachments.PLAYER_SKILLS);
        long desiredXp = proficiency.totalProficiencyLevel();
        long currentXp = skills.getXp(Skills.MAGICIAN_ID);
        if (desiredXp <= currentXp) {
            return;
        }
        long newXp = skills.addXp(Skills.MAGICIAN_ID, desiredXp - currentXp);
        player.syncData(ModAttachments.PLAYER_SKILLS);
        if (!Lyfe.isMaxLevel(player, Skills.MAGICIAN_ID)) {
            PacketDistributor.sendToPlayer(player, new XpGainPayload(Skills.MAGICIAN_ID, currentXp, newXp));
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
