package com.github.cerealklla.lyfe.combat;

/**
 * In-memory-only per-entity combat tracking backing "Surviving Combat" (design doc, 2026-10-20 user
 * spec: "10 seconds after a player leaves combat and didn't die. This means no longer taking damage
 * from or dealing damage to an entity. Does not include healing another NPC/Player"). Same
 * in-memory-only scoping as {@link AssistMarkers} -- never needs to survive a save/reload or be
 * visible client-side, so no {@code serialize}/{@code sync} on its {@code AttachmentType}.
 *
 * <p>{@code lastCombatGameTime} is stamped by {@link CombatStateTracker} on any damage dealt or
 * received (not healing, per spec). {@code lastConsumedCombatGameTime} tracks which combat session
 * a caller has already been credited for "surviving," so {@link #tryConsumeSurvivedCombat} fires
 * exactly once per session rather than repeatedly once 10 seconds have passed -- comparing the two
 * longs directly means a fresh combat engagement (which bumps {@code lastCombatGameTime} again)
 * automatically re-arms it with no separate boolean flag to reset.
 */
public final class CombatState {

    private static final long SURVIVED_COMBAT_TICKS = 10 * 20;

    private long lastCombatGameTime = Long.MIN_VALUE;
    private long lastConsumedCombatGameTime = Long.MIN_VALUE;

    public void stampCombat(long gameTime) {
        lastCombatGameTime = gameTime;
    }

    /**
     * True at most once per combat session, the first time this is called at least 10 seconds after
     * the last stamped combat event. Immediately marks that session as consumed so a repeated call
     * (e.g. a per-tick scan) doesn't keep re-triggering until combat is re-engaged.
     */
    public boolean tryConsumeSurvivedCombat(long gameTime) {
        if (lastCombatGameTime == Long.MIN_VALUE || lastCombatGameTime == lastConsumedCombatGameTime) {
            return false;
        }
        if (gameTime - lastCombatGameTime < SURVIVED_COMBAT_TICKS) {
            return false;
        }
        lastConsumedCombatGameTime = lastCombatGameTime;
        return true;
    }
}
