package com.github.cerealklla.lyfe.magician;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.resources.Identifier;

/**
 * In-memory-only per-entity spell cooldown tracking, keyed by spell id -- attached to any {@code
 * LivingEntity} (player or NPC), same scoping as {@code combat.CombatState}. Deliberately NOT
 * vanilla's own {@code Player#getCooldowns()}: that's Player-only (no equivalent exists for an NPC
 * caster, see {@code guardhouse}'s future Nature-Staff-wielding Guard), and this one mechanism is
 * reused by both {@code SparkOfLifeCasting} (the actual gate) and {@code magician.NatureStaffItem}
 * (which separately also sets vanilla's own cooldown purely for its free cosmetic hotbar swipe --
 * harmless, since nothing here depends on vanilla's cooldown to function).
 *
 * <p>Also tracks the Casting Speed global gate ({@link #isGloballyOnCooldown}/{@link
 * #startGlobalCooldown}, added 2026-10-21) -- a single cross-spell cooldown separate from any one
 * spell's own per-spell entry above, so casting one spell also locks out casting any *other* spell
 * for {@code registration.ModAttributes#CASTING_SPEED} seconds (explicit user spec: "if we had 2
 * different spells you can't cast them on top of each other"). A caster must clear both gates to
 * cast -- whichever is further out in the future wins.
 */
public final class SpellCooldowns {

    private final Map<Identifier, Long> readyAtGameTime = new HashMap<>();
    private long globalReadyAtGameTime;

    public boolean isOnCooldown(Identifier spellId, long now) {
        return now < readyAtGameTime.getOrDefault(spellId, 0L);
    }

    public void startCooldown(Identifier spellId, long now, int durationTicks) {
        readyAtGameTime.put(spellId, now + durationTicks);
    }

    public boolean isGloballyOnCooldown(long now) {
        return now < globalReadyAtGameTime;
    }

    public void startGlobalCooldown(long now, int durationTicks) {
        globalReadyAtGameTime = now + durationTicks;
    }
}
