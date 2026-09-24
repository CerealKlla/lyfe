package com.github.cerealklla.lyfe.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class SkillDefinitionTest {

    @Test
    void effectsStackFromEveryThresholdAtOrBelowLevel() {
        SkillDefinition definition = new SkillDefinition(
                new SkillId("test_stacking"),
                "Test Stacking",
                SkillCategory.GATHERING,
                Optional.empty(),
                new XpCurve(List.of(10L, 20L, 30L)),
                List.of(
                        new SkillEffect(1, EffectType.SPEED_MULTIPLIER, 1.1),
                        new SkillEffect(2, EffectType.BONUS_YIELD_CHANCE, 0.05),
                        new SkillEffect(3, EffectType.WHOLE_STRUCTURE_CHANCE, 0.02)
                )
        );

        assertEquals(List.of(), definition.effectsUpToLevel(0));
        assertEquals(1, definition.effectsUpToLevel(1).size());
        assertEquals(2, definition.effectsUpToLevel(2).size());
        assertEquals(3, definition.effectsUpToLevel(3).size());
        assertEquals(3, definition.effectsUpToLevel(99).size());
    }
}
