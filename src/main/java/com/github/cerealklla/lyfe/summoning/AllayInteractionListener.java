package com.github.cerealklla.lyfe.summoning;

import java.util.Optional;

import com.github.cerealklla.lyfe.registration.ModAttachments;
import com.github.cerealklla.lyfe.registration.ModItems;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Three real behaviors consolidated into one listener since they all react to the exact same event
 * (design doc Section 27):
 * <ol>
 *   <li><b>Bind</b> -- a plain Glass Bottle right-clicked onto a live, not-already-owned Allay opens
 *   the naming screen ({@link AllayBindListener#requestBind}).</li>
 *   <li><b>Duplicate</b> -- the owner handing a dancing, gather-stocked *owned* Allay an Amethyst
 *   Shard (or anything in {@link ItemTags#DUPLICATES_ALLAYS}) mints a second Allay Bottle with
 *   empty Equip/Gather slots, instead of vanilla's own loose-wild-duplicate mechanic (recreated as
 *   our own mechanic since the global disable below removes vanilla's version as a side effect --
 *   explicit user request, scoped to bound Allays only).</li>
 *   <li><b>Global disable</b> -- every other interaction (vanilla's own "give it an item" gesture)
 *   is cancelled outright, for literally every Allay, bound or wild -- explicit user request. A
 *   real, accepted side effect: this also removes vanilla's own Allay duplication (which depends on
 *   that exact interaction), not overlooked, see (2) above for the replacement.</li>
 * </ol>
 */
public final class AllayInteractionListener {

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getTarget() instanceof Allay allay)) {
            return;
        }
        event.setCanceled(true);

        ItemStack held = player.getItemInHand(event.getHand());
        Optional<SummonedAllayOwner> owner = allay.getExistingData(ModAttachments.SUMMONED_ALLAY_OWNER);

        if (held.is(Items.GLASS_BOTTLE) && owner.isEmpty()) {
            AllayBindListener.requestBind(player, allay);
            return;
        }

        if (held.is(ItemTags.DUPLICATES_ALLAYS) && owner.isPresent() && owner.get().playerId().equals(player.getUUID())
                && allay.isDancing() && !allay.getItemInHand(InteractionHand.MAIN_HAND).isEmpty()) {
            duplicateBottle(player, held);
        }
        // Every other case (any other held item, or empty hand, on any Allay at all) is simply
        // cancelled above with no further action -- the explicit global disable.
    }

    private void duplicateBottle(ServerPlayer player, ItemStack shard) {
        ItemStack main = player.getMainHandItem();
        ItemStack bottle = main.getItem() instanceof AllayBottleItem ? main : player.getOffhandItem();
        if (!(bottle.getItem() instanceof AllayBottleItem) || !(bottle.get(ModItems.ALLAY_BOTTLE_DATA.get()) instanceof AllayBottleData data)) {
            return;
        }
        ItemStack newBottle = new ItemStack(ModItems.ALLAY_BOTTLE.get());
        newBottle.set(ModItems.ALLAY_BOTTLE_DATA.get(), data.withEmptySlots().withSummonedAllayId(Optional.empty()));
        shard.shrink(1);
        if (!player.getInventory().add(newBottle)) {
            player.drop(newBottle, false);
        }
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Duplicated \"" + data.displayName() + "\" into a new bottle."));
    }
}
