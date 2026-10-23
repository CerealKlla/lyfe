package com.github.cerealklla.lyfe.summoning;

import java.util.Optional;

import com.github.cerealklla.lyfe.registration.ModAttachments;
import com.github.cerealklla.lyfe.registration.ModItems;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The "bind an Allay to a bottle" flow (design doc Section 27) -- mirrors {@link
 * MountBindListener}'s own validate -> open-confirm -> re-validate-and-finalize shape, but triggered
 * by a direct world interaction ({@code AllayInteractionListener}, a plain Glass Bottle right-clicked
 * onto a live, unowned Allay) rather than a GUI button, so there's no separate "request" payload at
 * all -- the interaction event itself is already server-side.
 *
 * <p>Discards the original wild Allay the instant a bind succeeds (same "otherwise re-binding the
 * same Allay would duplicate it for free" reasoning {@link MountBindListener} already documents).
 */
public final class AllayBindListener {

    private AllayBindListener() {
    }

    /** Already-validated by the caller (a live, not-already-owned Allay, a real Glass Bottle in hand) -- just opens the naming screen. */
    public static void requestBind(ServerPlayer player, Allay allay) {
        PacketDistributor.sendToPlayer(player, new OpenAllayBindNameScreenPayload(allay.getId()));
    }

    public static void confirmBind(ServerPlayer player, ConfirmAllayBindPayload payload) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (!(level.getEntity(payload.allayEntityId()) instanceof Allay allay) || !allay.isAlive()) {
            player.sendSystemMessage(Component.literal("That Allay is gone."));
            return;
        }
        // Never trust the client alone -- re-check this Allay isn't already bound/summoned (e.g. a
        // second player's bottle) and that the requester is actually still close enough to be
        // interacting with it, same "never trust who's clicking" discipline this suite already
        // applies everywhere else.
        if (allay.getExistingData(ModAttachments.SUMMONED_ALLAY_OWNER).isPresent()) {
            player.sendSystemMessage(Component.literal("That Allay is already bound to a bottle."));
            return;
        }
        if (player.distanceToSqr(allay) > 36.0) {
            player.sendSystemMessage(Component.literal("You're not close enough to that Allay anymore."));
            return;
        }
        String name = payload.name().isBlank() ? "Allay" : payload.name();
        allay.setCustomName(Component.literal(name));

        CompoundTag savedState = AllayCapture.capture(allay, level);
        allay.discard();

        ItemStack bottle = new ItemStack(ModItems.ALLAY_BOTTLE.get());
        bottle.set(ModItems.ALLAY_BOTTLE_DATA.get(), new AllayBottleData(savedState, 0L, Optional.empty(), ItemStack.EMPTY, ItemStack.EMPTY, name));
        // Consumes the Glass Bottle that triggered the bind -- same one-bottle-per-Allay cost shape
        // as the original interaction, re-checked here rather than trusted from the event handler.
        consumeOneGlassBottle(player);
        if (!player.getInventory().add(bottle)) {
            player.drop(bottle, false);
        }
        player.sendSystemMessage(Component.literal("Bound \"" + name + "\" to an Allay in a Bottle."));
    }

    /** "Rename" on {@code client.AllayBottleScreen} (design doc Section 27). Re-resolves the bottle from whichever hand the player is actually holding it in -- same discipline as {@link AllayBottleMenu} itself. */
    public static void renameBottle(ServerPlayer player, RenameAllayBottlePayload payload) {
        if (payload.name().isBlank()) {
            return;
        }
        ItemStack main = player.getMainHandItem();
        ItemStack bottle = main.getItem() instanceof AllayBottleItem ? main : player.getOffhandItem();
        if (!(bottle.getItem() instanceof AllayBottleItem) || !(bottle.get(ModItems.ALLAY_BOTTLE_DATA.get()) instanceof AllayBottleData data)) {
            return;
        }
        bottle.set(ModItems.ALLAY_BOTTLE_DATA.get(), data.withDisplayName(payload.name()));
        if (data.summonedAllayId().isPresent() && player.level() instanceof ServerLevel level
                && level.getEntity(data.summonedAllayId().get()) instanceof Allay allay && allay.isAlive()) {
            allay.setCustomName(Component.literal(payload.name()));
        }
    }

    private static void consumeOneGlassBottle(ServerPlayer player) {
        ItemStack main = player.getMainHandItem();
        if (main.is(net.minecraft.world.item.Items.GLASS_BOTTLE)) {
            main.shrink(1);
            return;
        }
        ItemStack off = player.getOffhandItem();
        if (off.is(net.minecraft.world.item.Items.GLASS_BOTTLE)) {
            off.shrink(1);
        }
    }
}
