package com.github.cerealklla.lyfe.mana;

import com.github.cerealklla.lyfe.registration.ModAttachments;
import com.github.cerealklla.lyfe.registration.ModAttributes;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Per-second Mana regen for any {@link LivingEntity} (design doc, 2026-10-20: "Mana Regen...
 * influences how quickly mana is replenished"), reading {@link ModAttributes#MANA_REGEN}/{@link
 * ModAttributes#MAX_MANA}'s live values rather than caching either -- the same "never cache the
 * max, always re-derive it" discipline {@code swim.PlayerAir}'s own doc describes, since both can
 * change at any time (a Magician level-up, equipping/unequipping a Nature Staff, a Rune).
 *
 * <p>Guards on a non-null {@link AttributeInstance} rather than assuming universal coverage --
 * {@code registration.ModAttributes#onEntityAttributeModification} attaches these Attributes to
 * every entity type that already has an {@code AttributeSupplier} at all, which is effectively
 * every real mob/player, but an entity type with none at all (rare) would otherwise NPE here.
 */
public final class ManaListener {

    private static final int TICKS_PER_SECOND = 20;

    @SubscribeEvent
    public void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity entity) || entity.tickCount % TICKS_PER_SECOND != 0) {
            return;
        }
        AttributeInstance maxManaAttribute = entity.getAttribute(ModAttributes.MAX_MANA);
        AttributeInstance regenAttribute = entity.getAttribute(ModAttributes.MANA_REGEN);
        if (maxManaAttribute == null || regenAttribute == null) {
            return;
        }
        double regenPerSecond = regenAttribute.getValue();
        if (regenPerSecond <= 0.0) {
            return;
        }
        double maxMana = maxManaAttribute.getValue();
        EntityMana mana = entity.getData(ModAttachments.ENTITY_MANA);
        double before = mana.getCurrentMana();
        mana.regen(regenPerSecond, maxMana);
        if (mana.getCurrentMana() != before) {
            entity.syncData(ModAttachments.ENTITY_MANA);
        }
    }
}
