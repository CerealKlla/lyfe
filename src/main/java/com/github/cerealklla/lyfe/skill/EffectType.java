package com.github.cerealklla.lyfe.skill;

/**
 * Composable passive effect kinds (design doc Section 4) — most new skills should reuse one of
 * these rather than needing bespoke code.
 */
public enum EffectType {
    SPEED_MULTIPLIER,
    BONUS_YIELD_CHANCE,
    WHOLE_STRUCTURE_CHANCE,
    TOOL_TIER_GATE,
    DAMAGE_MULTIPLIER,
    DURABILITY_LOSS_REDUCTION,
    CHANCE_EFFECT,
    INTERACTION_UNLOCK,
    GATHER_THRESHOLD_REDUCTION
}
