package com.github.cerealklla.lyfe.fishing;

import com.github.cerealklla.lyfe.registration.ModItems;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.ItemStackedOnOtherEvent;

/**
 * Drag a Key onto its matching Locked Chest in an inventory slot (design doc Section G) -- {@link
 * FishingConstants#KEY_UNLOCK_CHANCE} unlock chance, Key consumed either way. Uses {@code
 * ItemStackedOnOtherEvent} rather than {@code Item#overrideStackedOnOther} (the mechanism {@code
 * CatchBagItem}/vanilla Bundle use) since this is a "two distinct items combine into an outcome"
 * interaction, not a container accepting a payload -- the event is the cleaner single interception
 * point for that shape, confirmed via decompiled {@code AbstractContainerMenu
 * #tryItemClickBehaviourOverride} research.
 *
 * <p>Sends a direct result message either way (added 2026-10-11, explicit user request -- a player
 * previously had no feedback at all on whether the attempt actually unlocked the box or just broke
 * the key for nothing).
 */
public final class LockedChestUnlockListener {

    @SubscribeEvent
    public void onItemStackedOn(ItemStackedOnOtherEvent event) {
        if (event.getClickAction() != ClickAction.SECONDARY) {
            return;
        }
        ItemStack carried = event.getCarriedItem();
        ItemStack stackedOn = event.getStackedOnItem();
        if (!(carried.getItem() instanceof KeyItem key) || !(stackedOn.getItem() instanceof LockedChestItem chest)) {
            return;
        }
        if (!key.variant().equals(chest.variant()) || !LockedChestItem.isLocked(stackedOn)) {
            return;
        }

        event.setCanceled(true);
        RandomSource random = event.getPlayer().level().getRandom();
        boolean unlocked = random.nextDouble() < FishingConstants.KEY_UNLOCK_CHANCE;
        if (unlocked) {
            stackedOn.set(ModItems.LOCKED_CHEST_LOCKED, Boolean.FALSE);
        }
        event.getCarriedSlotAccess().set(carried.copyWithCount(carried.getCount() - 1));

        if (event.getPlayer() instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(unlocked
                    ? Component.literal("The key turns -- the lock clicks open!").withStyle(ChatFormatting.GREEN)
                    : Component.literal("The key snaps off in the lock...").withStyle(ChatFormatting.RED));
        }
    }
}
