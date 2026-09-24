package com.github.cerealklla.lyfe.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

// Uses unique, test-local skill ids throughout so these tests are independent of whatever else
// happens to already be registered in the shared static SkillRegistry (e.g. by other test classes).
class SkillRegistryTest {

    private static final XpCurve TRIVIAL_CURVE = new XpCurve(List.of(1L));

    @Test
    void registerThenGetReturnsTheSameDefinition() {
        SkillDefinition definition = new SkillDefinition(
                new SkillId("test_registry_basic"),
                "Test Registry Basic",
                SkillCategory.GATHERING,
                Optional.empty(),
                TRIVIAL_CURVE,
                List.of()
        );

        SkillRegistry.register(definition);

        assertEquals(Optional.of(definition), SkillRegistry.get(new SkillId("test_registry_basic")));
    }

    @Test
    void unregisteredIdReturnsEmpty() {
        assertEquals(Optional.empty(), SkillRegistry.get(new SkillId("test_registry_never_registered")));
    }

    @Test
    void availableFiltersOutSkillsWhoseRequiredModIsntLoaded() {
        SkillDefinition noDependency = new SkillDefinition(
                new SkillId("test_registry_no_dep"),
                "No Dependency",
                SkillCategory.GATHERING,
                Optional.empty(),
                TRIVIAL_CURVE,
                List.of()
        );
        SkillDefinition requiresLoadedMod = new SkillDefinition(
                new SkillId("test_registry_requires_loaded"),
                "Requires Loaded Mod",
                SkillCategory.KNOWLEDGE,
                Optional.of("present_mod"),
                TRIVIAL_CURVE,
                List.of()
        );
        SkillDefinition requiresMissingMod = new SkillDefinition(
                new SkillId("test_registry_requires_missing"),
                "Requires Missing Mod",
                SkillCategory.KNOWLEDGE,
                Optional.of("absent_mod"),
                TRIVIAL_CURVE,
                List.of()
        );

        SkillRegistry.register(noDependency);
        SkillRegistry.register(requiresLoadedMod);
        SkillRegistry.register(requiresMissingMod);

        Set<SkillId> available = SkillRegistry.available("present_mod"::equals).stream()
                .map(SkillDefinition::id)
                .filter(id -> id.value().startsWith("test_registry_"))
                .collect(java.util.stream.Collectors.toSet());

        assertTrue(available.contains(noDependency.id()));
        assertTrue(available.contains(requiresLoadedMod.id()));
        assertTrue(!available.contains(requiresMissingMod.id()));
    }
}
