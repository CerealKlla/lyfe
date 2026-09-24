package com.github.cerealklla.lyfe.api;

import java.util.Collection;
import java.util.Optional;

import com.github.cerealklla.lyfe.registration.ModAttachments;
import com.github.cerealklla.lyfe.skill.SkillDefinition;
import com.github.cerealklla.lyfe.skill.SkillId;
import com.github.cerealklla.lyfe.skill.SkillRegistry;

import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;

/**
 * The stable public entry point for a player's skill data. Other mods (and Lyfe's own skill
 * implementations) should call these rather than touch {@code PlayerSkills}/the attachment
 * directly, so internal storage changes never require dependents to rewrite their integration —
 * same pattern as Cartographyr's {@code Cartography} facade.
 */
public final class Lyfe {

    private Lyfe() {
    }

    public static long getXp(Player player, SkillId skillId) {
        return player.getData(ModAttachments.PLAYER_SKILLS).getXp(skillId);
    }

    /** Adds XP for a skill and returns the new total. Amounts that would take XP below 0 are clamped. */
    public static long addXp(Player player, SkillId skillId, long amount) {
        return player.getData(ModAttachments.PLAYER_SKILLS).addXp(skillId, amount);
    }

    /** The player's current level in a skill, per that skill's own XP curve (design doc Section 3). Unregistered skills report level 0. */
    public static int getLevel(Player player, SkillId skillId) {
        long xp = getXp(player, skillId);
        return getSkillDefinition(skillId)
                .map(def -> def.xpCurve().levelForXp(xp))
                .orElse(0);
    }

    public static Optional<SkillDefinition> getSkillDefinition(SkillId skillId) {
        return SkillRegistry.get(skillId);
    }

    /** Every registered skill visible right now — excludes skills whose required mod isn't loaded (design doc Section 8). */
    public static Collection<SkillDefinition> getAvailableSkills() {
        return SkillRegistry.available(ModList.get()::isLoaded);
    }
}
