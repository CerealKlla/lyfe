package com.github.cerealklla.lyfe.magician;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import com.github.cerealklla.lyfe.combat.TagEffectivenessTable;
import com.github.cerealklla.lyfe.registration.ModAttachments;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;

/**
 * Per-caster cache of {@link #canSpellAffectEntity} results, keyed by target entity id then spell
 * id -- so an AI goal re-evaluating the same nearby target every tick doesn't re-walk every
 * effect's relationship/tag checks each time (mainly to stop AI from repeatedly attempting a spell
 * against a target it can never affect). Bounded LRU (unlike {@code combat.AssistMarkers}, which
 * stays small purely because its entries expire in 15 seconds) -- this cache has no natural
 * expiry, so an unbounded map would leak slowly for a long-lived roaming NPC evaluating many
 * distinct targets over a session. 32 recent targets is already far more than any goal scans
 * concurrently. Same in-memory-only, non-persisted, non-synced attachment shape as {@code
 * AssistMarkers}, attached to the caster.
 */
public final class SpellTargetCache {

    private static final int MAX_TARGETS = 32;

    private final Map<Integer, Map<Identifier, Boolean>> byTarget = new LinkedHashMap<>(MAX_TARGETS, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, Map<Identifier, Boolean>> eldest) {
            return size() > MAX_TARGETS;
        }
    };

    private Boolean get(int targetId, Identifier spellId) {
        Map<Identifier, Boolean> bySpell = byTarget.get(targetId);
        return bySpell == null ? null : bySpell.get(spellId);
    }

    private void put(int targetId, Identifier spellId, boolean result) {
        byTarget.computeIfAbsent(targetId, k -> new HashMap<>()).put(spellId, result);
    }

    /** Called whenever {@code caster}'s own available spells change (e.g. an equip-slot change) -- see callers for the exact hook points. */
    public void invalidateAll() {
        byTarget.clear();
    }

    /** Whether any effect of {@code spell} could currently affect {@code target} if {@code caster} cast it -- cached per (target, spell). */
    public static boolean canSpellAffectEntity(LivingEntity caster, Spell spell, LivingEntity target) {
        SpellTargetCache cache = caster.getData(ModAttachments.SPELL_TARGET_CACHE);
        Boolean cached = cache.get(target.getId(), spell.id());
        if (cached != null) {
            return cached;
        }
        boolean result = false;
        for (SpellEffect effect : spell.effects()) {
            if (canEffectAffectEntity(effect, caster, target)) {
                result = true;
                break;
            }
        }
        cache.put(target.getId(), spell.id(), result);
        return result;
    }

    static boolean canEffectAffectEntity(SpellEffect effect, LivingEntity caster, LivingEntity target) {
        return SpellTargeting.matches(effect.targetList(), caster, target)
                && TagEffectivenessTable.multiplierFor(effect.combinedTags(), target) > 0.0F;
    }

    public static void invalidate(LivingEntity caster) {
        caster.getData(ModAttachments.SPELL_TARGET_CACHE).invalidateAll();
    }
}
