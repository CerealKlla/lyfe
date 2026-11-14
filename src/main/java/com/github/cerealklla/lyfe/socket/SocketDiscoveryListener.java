package com.github.cerealklla.lyfe.socket;

import com.github.cerealklla.lyfe.registration.ModAttachments;
import com.github.cerealklla.lyfe.registration.ModItems;
import com.github.cerealklla.lyfe.research.PlayerResearch;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;

/**
 * "Players should learn the recipe for the die grinder and pry bar the first time they find a rune
 * or socketed item" (explicit user spec, 2026-10-18) -- same {@code ItemEntityPickupEvent.Post}
 * trigger {@code currency.CoinPurseListener#onItemPickup} already uses for its own "the moment you
 * physically pick something up" mechanic. Learns both recipes outright via {@code
 * research.PlayerResearch#learnDirectly}, the same real (non-debug) auto-grant mechanism {@code
 * cook.FoodStarterPackListener}'s Cook starter pack already uses -- finding evidence the Socketing
 * system exists at all is treated as reason enough to skip researching the two tools that work it,
 * rather than needing a full normal Research Bench grind for either.
 *
 * <p>No separate "already granted" flag needed, unlike the Food Starter Pack (which needed one
 * because each grant was a random pick and so not naturally idempotent) -- {@code
 * PlayerResearch#addResearchPoints}/{@code #learnDirectly} are already documented no-ops once a
 * recipe is learned, so repeatedly picking up runes/socketed gear after the first costs nothing
 * extra and never re-sends the message.
 */
public final class SocketDiscoveryListener {

    @SubscribeEvent
    public void onItemPickup(ItemEntityPickupEvent.Post event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || !isRuneOrSocketedItem(event.getOriginalStack())) {
            return;
        }
        PlayerResearch research = player.getData(ModAttachments.PLAYER_RESEARCH);
        boolean learnedGrinder = research.learnDirectly(SocketToolRegistry.dieGrinderId(), SocketToolRegistry.RECIPE_TIER);
        boolean learnedPryBar = research.learnDirectly(SocketToolRegistry.pryBarId(), SocketToolRegistry.RECIPE_TIER);
        if (learnedGrinder || learnedPryBar) {
            // Mutating the object getData() returns does NOT trigger a client resync on its own --
            // needed now that this attachment is synced (2026-10-17, for the Crafting Guide tab).
            player.syncData(ModAttachments.PLAYER_RESEARCH);
            player.sendSystemMessage(Component.literal("You've learned how to craft the Diamond Die Grinder and Diamond Pry Bar!"));
        }

        // Rune Codex (2026-10-17) -- same trigger, records every distinct RuneId actually present
        // (a bare Rune, or any RUNE-kind socket on a picked-up item), not just "a rune was involved."
        if (recordDiscoveredRunes(player, event.getOriginalStack())) {
            player.syncData(ModAttachments.PLAYER_DISCOVERED_RUNES);
        }
    }

    /**
     * Real gap found 2026-11-14 -- a Rune taken out of a container via the inventory GUI (a loot
     * chest, any chest, a Construction Box, etc.) never fires {@link ItemEntityPickupEvent} at all
     * (nothing was picked up off the ground), so it silently never got recorded -- confirmed live:
     * "Wyn" was visibly held/tooltipped but absent from an otherwise-alphabetically-complete Codex
     * list. {@link RuneBackfillListener} can't help either, since it's a genuine one-time migration,
     * gated to never run twice for the same player. Same "react to container interaction instead of
     * polling" fix this exact gap already got elsewhere in this suite (`GuardEquipBoxChangeListener`/
     * `CoinPurseListener`/`RecallciniteListener` all use this identical event for this identical
     * reason) -- scans the player's whole inventory on every container close, same scan
     * {@link RuneBackfillListener} itself already uses, just not limited to running once.
     */
    @SubscribeEvent
    public void onContainerClose(PlayerContainerEvent.Close event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        boolean any = false;
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty()) {
                any |= recordDiscoveredRunes(player, stack);
            }
        }
        if (any) {
            player.syncData(ModAttachments.PLAYER_DISCOVERED_RUNES);
        }
    }

    /**
     * @return true if at least one previously-undiscovered {@link RuneId} was just recorded.
     * Public, not package-private -- widened 2026-11-14 for {@code fishing.SunkenTreasureItem}/
     * {@code LockedChestItem}'s own direct call sites (see their own doc for why).
     */
    public static boolean recordDiscoveredRunes(ServerPlayer player, ItemStack stack) {
        var discovered = player.getData(ModAttachments.PLAYER_DISCOVERED_RUNES);
        boolean any = false;
        RuneId bareRuneId = RuneItem.runeIdOf(stack);
        if (bareRuneId != null) {
            any |= discovered.markDiscovered(bareRuneId);
        }
        SocketData data = stack.get(ModItems.SOCKET_DATA.get());
        if (data != null) {
            for (SocketSlot slot : data.sockets()) {
                if (slot.kind() == SocketSlot.Kind.RUNE) {
                    any |= discovered.markDiscovered(slot.runeId().orElseThrow());
                }
            }
        }
        return any;
    }

    private static boolean isRuneOrSocketedItem(ItemStack stack) {
        if (stack.is(ModItems.RUNE.get())) {
            return true;
        }
        SocketData data = stack.get(ModItems.SOCKET_DATA.get());
        return data != null && !data.sockets().isEmpty();
    }
}
