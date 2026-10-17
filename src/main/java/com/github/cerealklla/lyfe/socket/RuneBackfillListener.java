package com.github.cerealklla.lyfe.socket;

import com.github.cerealklla.lyfe.registration.ModAttachments;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * One-time Rune Codex backfill (2026-10-17) -- a player who already held or socketed Runes before
 * this feature existed would otherwise see an empty Codex forever, since {@link
 * SocketDiscoveryListener} only records a Rune at the moment it's physically picked up. On a
 * player's first login after this ships, scans their full inventory (hotbar, main storage, armor,
 * offhand -- {@code Inventory#getContainerSize()} covers all of it in one indexed container, same
 * convention Settlemynts' own full-inventory scans already use) for bare Runes or already-socketed
 * items, same {@link SocketDiscoveryListener#recordDiscoveredRunes} logic a real pickup uses.
 * Gated on {@link PlayerDiscoveredRunes#isBackfilled()} so it can never run twice for the same
 * player -- a server wipe resets the flag along with everything else in the attachment, which is
 * correct (a genuinely new save has nothing to backfill from anyway).
 */
public final class RuneBackfillListener {

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        var discovered = player.getData(ModAttachments.PLAYER_DISCOVERED_RUNES);
        if (discovered.isBackfilled()) {
            return;
        }
        boolean any = false;
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty()) {
                any |= SocketDiscoveryListener.recordDiscoveredRunes(player, stack);
            }
        }
        discovered.markBackfilled();
        player.syncData(ModAttachments.PLAYER_DISCOVERED_RUNES);
    }
}
