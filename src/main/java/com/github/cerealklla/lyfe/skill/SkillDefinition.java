package com.github.cerealklla.lyfe.skill;

import java.util.List;
import java.util.Optional;

/**
 * Describes a skill: id, display info, category, an optional hard dependency on another mod
 * (design doc Section 3), its XP curve, and its passive effects. Skills are data, not bespoke
 * per-skill code — adding a new skill should mostly mean constructing one of these.
 */
public record SkillDefinition(
        SkillId id,
        String displayName,
        SkillCategory category,
        Optional<String> requiredModId,
        XpCurve xpCurve,
        List<SkillEffect> effects
) {
    public SkillDefinition {
        effects = List.copyOf(effects);
    }

    /** Every effect whose threshold is at or below {@code level} (design doc Section 4: effects stack). */
    public List<SkillEffect> effectsUpToLevel(int level) {
        return effects.stream().filter(e -> e.levelThreshold() <= level).toList();
    }
}
