package com.github.cerealklla.lyfe.fishing;

import java.util.Optional;

import com.github.cerealklla.lyfe.registration.ModItems;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Holds caught fish up to a total weight cap rather than a slot/item-count cap (design doc Section
 * H). Direct template: Yconomics' {@code CoinPurseItem} ({@code overrideStackedOnOther}/{@code
 * overrideOtherStackedOnMe}, the exact same vanilla-Bundle interception points, no custom menu).
 */
public class CatchBagItem extends Item {

    public CatchBagItem(Properties properties) {
        super(properties);
    }

    private static CatchBagContents contentsOf(ItemStack stack) {
        return stack.getOrDefault(ModItems.CATCH_BAG_CONTENTS, CatchBagContents.EMPTY);
    }

    @Override
    public Component getName(ItemStack itemStack) {
        return Component.literal("Catch Bag");
    }

    /**
     * The same grid-of-icons tooltip preview vanilla's real Bundle uses (2026-10-03 playtest
     * request), via this bag's own {@link CatchBagTooltip} -- not vanilla's {@code BundleTooltip},
     * whose progress bar is driven by item-count-vs-max-stack-size and doesn't mean anything for a
     * pounds-capped bag (see that class's own doc for the live bug this replaced).
     */
    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack itemStack) {
        CatchBagContents contents = contentsOf(itemStack);
        if (contents.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new CatchBagTooltip(contents.stacks(), contents.totalPounds(), FishingConstants.CATCH_BAG_MAX_POUNDS));
    }

    @Override
    public boolean overrideStackedOnOther(ItemStack self, Slot slot, ClickAction clickAction, Player player) {
        ItemStack clicked = slot.getItem();
        if (clickAction == ClickAction.PRIMARY && !clicked.isEmpty()) {
            if (!CatchBagContents.isFish(clicked)) {
                return false;
            }
            CatchBagContents.InsertResult result = contentsOf(self).insert(clicked);
            if (result.inserted()) {
                self.set(ModItems.CATCH_BAG_CONTENTS, result.contents());
                slot.set(ItemStack.EMPTY);
                broadcastChanges(player);
            }
            return true;
        } else if (clickAction == ClickAction.SECONDARY && clicked.isEmpty()) {
            CatchBagContents.RemoveResult result = contentsOf(self).removeLast();
            if (!result.removed().isEmpty()) {
                self.set(ModItems.CATCH_BAG_CONTENTS, result.contents());
                ItemStack remainder = slot.safeInsert(result.removed());
                if (!remainder.isEmpty() && !player.getInventory().add(remainder)) {
                    player.drop(remainder, false);
                }
                broadcastChanges(player);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean overrideOtherStackedOnMe(ItemStack self, ItemStack other, Slot slot, ClickAction clickAction, Player player, SlotAccess carriedItem) {
        if (clickAction != ClickAction.PRIMARY || other.isEmpty() || !CatchBagContents.isFish(other) || !slot.allowModification(player)) {
            return false;
        }
        CatchBagContents.InsertResult result = contentsOf(self).insert(other);
        if (result.inserted()) {
            self.set(ModItems.CATCH_BAG_CONTENTS, result.contents());
            other.setCount(0);
            broadcastChanges(player);
        }
        return true;
    }

    private static void broadcastChanges(Player player) {
        if (player.containerMenu != null) {
            player.containerMenu.slotsChanged(player.getInventory());
        }
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        ItemStack bag = player.getItemInHand(hand);
        CatchBagContents.RemoveResult result = contentsOf(bag).removeLast();
        if (result.removed().isEmpty()) {
            return InteractionResult.CONSUME;
        }
        bag.set(ModItems.CATCH_BAG_CONTENTS, result.contents());
        if (!player.getInventory().add(result.removed())) {
            player.drop(result.removed(), false);
        }
        return InteractionResult.SUCCESS_SERVER;
    }
}
