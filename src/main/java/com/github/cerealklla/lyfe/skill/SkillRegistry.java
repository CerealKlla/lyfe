package com.github.cerealklla.lyfe.skill;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * In-memory registry of {@link SkillDefinition}s. Not a Minecraft datapack registry — skills are
 * described in code (design doc Section 3), not loaded from data files, so a simple map is enough.
 */
public final class SkillRegistry {

    private static final Map<SkillId, SkillDefinition> SKILLS = new LinkedHashMap<>();

    private SkillRegistry() {
    }

    public static void register(SkillDefinition definition) {
        SKILLS.put(definition.id(), definition);
    }

    public static Optional<SkillDefinition> get(SkillId id) {
        return Optional.ofNullable(SKILLS.get(id));
    }

    public static Collection<SkillDefinition> all() {
        return List.copyOf(SKILLS.values());
    }

    /**
     * Every registered skill whose {@link SkillDefinition#requiredModId()} (if any) satisfies
     * {@code isModLoaded} (design doc Section 8). Takes the check as a parameter rather than calling
     * {@code ModList.get()} directly so this stays a plain-JUnit-testable pure function; the real
     * mod-loading check is wired in by {@code com.github.cerealklla.lyfe.api.Lyfe}.
     */
    public static Collection<SkillDefinition> available(Predicate<String> isModLoaded) {
        return SKILLS.values().stream()
                .filter(d -> d.requiredModId().map(isModLoaded::test).orElse(true))
                .toList();
    }
}
