package com.github.cerealklla.lyfe.socket;

import com.github.cerealklla.lyfe.registration.ModAttachments;
import com.github.cerealklla.lyfe.registration.ModItems;
import com.github.cerealklla.lyfe.research.PlayerResearch;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;

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
    }

    private static boolean isRuneOrSocketedItem(ItemStack stack) {
        if (stack.is(ModItems.RUNE.get())) {
            return true;
        }
        SocketData data = stack.get(ModItems.SOCKET_DATA.get());
        return data != null && !data.sockets().isEmpty();
    }
}
