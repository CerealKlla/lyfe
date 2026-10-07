package com.github.cerealklla.lyfe.durability;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Part of the Unbreakable Equipment feature (2026-10-06): once a tool/weapon reaches
 * {@link UnbreakableConstants#isAtZero}, it has no effect when used -- it never actually deletes
 * (that's the {@code durability.mixin.ItemStackUnbreakableMixin} mixin's job), but it behaves as if
 * the player weren't holding a tool/weapon at all.
 *
 * <p>Mining: zeroing {@link PlayerEvent.BreakSpeed}'s new speed makes it exactly as ineffective as
 * vanilla's own "can't harvest this block" case -- no custom block-break cancellation needed.
 *
 * <p>Combat: scoped to melee only (mirrors {@code craft.ProficiencyDurabilityListener}'s own scope),
 * via {@link LivingIncomingDamageEvent}, zeroing the attacker's dealt damage when their main-hand
 * weapon is at zero. Bow/trident/other ranged damage sources aren't covered by this pass.
 */
public final class UnbreakableToolListener {

    @SubscribeEvent
    public void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        ItemStack tool = event.getEntity().getMainHandItem();
        if (UnbreakableConstants.isAtZero(tool)) {
            event.setNewSpeed(0.0f);
        }
    }

    @SubscribeEvent
    public void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker) || event.getAmount() <= 0) {
            return;
        }
        ItemStack weapon = attacker.getMainHandItem();
        if (UnbreakableConstants.isAtZero(weapon)) {
            event.setAmount(0.0f);
        }
    }
}
