package com.github.cerealklla.lyfe.skill;

/**
 * Composable passive effect kinds (design doc Section 4) — most new skills should reuse one of
 * these rather than needing bespoke code.
 */
public enum EffectType {
    SPEED_MULTIPLIER,
    BONUS_YIELD_CHANCE,
    WHOLE_STRUCTURE_CHANCE,
    /**
     * Deferred (2026-09-24, see decisions.md): not implemented anywhere yet. Design doc Section 7's
     * "tool above your unlocked tier deals 0 damage" mechanic is meant to gate the future crafting
     * overhaul's own tool tiers (Section 10), which don't have a concrete design yet -- gating
     * vanilla tool material tiers in the meantime was considered and rejected as throwaway work.
     * Pick this back up once the crafting overhaul has a real tier system to gate against.
     */
    TOOL_TIER_GATE,
    DAMAGE_MULTIPLIER,
    DURABILITY_LOSS_REDUCTION,
    CHANCE_EFFECT,
    INTERACTION_UNLOCK,
    GATHER_THRESHOLD_REDUCTION
}
