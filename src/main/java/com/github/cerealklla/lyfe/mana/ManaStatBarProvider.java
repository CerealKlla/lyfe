package com.github.cerealklla.lyfe.mana;

import com.github.cerealklla.lyfe.registration.ModAttachments;
import com.github.cerealklla.lyfe.registration.ModAttributes;

import com.github.cerealklla.nuclyus.statbar.StatBarProvider;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;

/**
 * Mana's own floating bar (design doc, 2026-10-20: "... above NPC heads when not at 100%"),
 * registered with Nuclyus's generic {@code statbar.StatBarRegistry} -- replaces the old {@code
 * ManaNameplate}/{@code api.Lyfe#manaNameplateLine} + Settlemynts' {@code bridge.LyfeManaBridge}
 * detour through {@code npc.NpcHealthBar}'s nametag hijack (explicit user request, 2026-10-21: "the
 * nametag style isn't sufficient, and I want to be able to have the guards have names in the
 * future"). Registered unconditionally in {@code LyfeMod#commonSetup} -- no {@code ModList.isLoaded}
 * guard needed, since Nuclyus is a hard-required dependency for every mod in this suite, unlike the
 * old Settlemynts-specific bridge this replaces.
 */
public final class ManaStatBarProvider implements StatBarProvider {

    private static final int COLOR = 0x1E90FF;

    @Override
    public boolean isApplicable(LivingEntity entity) {
        AttributeInstance maxMana = entity.getAttribute(ModAttributes.MAX_MANA);
        if (maxMana == null || maxMana.getValue() <= 0.0) {
            return false;
        }
        return entity.getData(ModAttachments.ENTITY_MANA).getCurrentMana() < maxMana.getValue();
    }

    @Override
    public float current(LivingEntity entity) {
        return (float) entity.getData(ModAttachments.ENTITY_MANA).getCurrentMana();
    }

    @Override
    public float max(LivingEntity entity) {
        AttributeInstance maxMana = entity.getAttribute(ModAttributes.MAX_MANA);
        return maxMana == null ? 0.0F : (float) maxMana.getValue();
    }

    @Override
    public int color() {
        return COLOR;
    }
}
