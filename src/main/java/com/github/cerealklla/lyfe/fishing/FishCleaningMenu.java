package com.github.cerealklla.lyfe.fishing;

import com.github.cerealklla.lyfe.registration.ModItems;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The Fish Cleaning Station's menu (rebuilt 2026-10-05, replacing the original one-click
 * interaction -- user feedback: "let's make it show a UI with 1 slot where you can add a fish,
 * there are also 2 buttons 'Clean Single Fish' and 'Clean All Fish'"). One input slot ({@link
 * FishOnlySlot}) plus player inventory, two buttons ({@link #clickMenuButton}): Clean Single
 * requires manually placing a fish in the slot; Clean All sweeps every fish-carrying stack out of
 * the player's own inventory and out of any held {@link CatchBagItem} bags, cleaning all of them in
 * one go. Interaction-driven logic lives here rather than a separate listener, same precedent as
 * {@code research.ResearchMenu}.
 */
public class FishCleaningMenu extends AbstractContainerMenu {

    private static final int INPUT_SLOT_X = 16;
    private static final int INPUT_SLOT_Y = 34;
    // 2026-10-05 playtest fix: was 66, which let the "Inventory" label (drawn at PLAYER_INV_Y - 10)
    // overlap the bottom of the "Clean All Fish" button (second button bottom edge at y+64).
    private static final int PLAYER_INV_Y = 86;
    public static final int CLEAN_SINGLE_BUTTON_ID = 0;
    public static final int CLEAN_ALL_BUTTON_ID = 1;

    private final Container inputContainer;

    public FishCleaningMenu(MenuType<?> type, int containerId, Inventory inventory, Container inputContainer) {
        super(type, containerId);
        this.inputContainer = inputContainer;
        layoutSlots(inventory);
    }

    /** Client-side reconstruction (see {@code registration.ModMenus}) -- no real station to read from. */
    public FishCleaningMenu(MenuType<?> type, int containerId, Inventory inventory) {
        this(type, containerId, inventory, new SimpleContainer(1));
    }

    private void layoutSlots(Inventory inventory) {
        addSlot(new FishOnlySlot(inputContainer, 0, INPUT_SLOT_X, INPUT_SLOT_Y));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, PLAYER_INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, PLAYER_INV_Y + 58));
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        if (id == CLEAN_SINGLE_BUTTON_ID) {
            return cleanSingle(serverPlayer);
        } else if (id == CLEAN_ALL_BUTTON_ID) {
            return cleanAll(serverPlayer);
        }
        return false;
    }

    private boolean cleanSingle(ServerPlayer player) {
        ItemStack fish = inputContainer.getItem(0);
        if (fish.isEmpty() || !CatchBagContents.isFish(fish)) {
            player.sendSystemMessage(Component.literal("Place a fish in the slot first."));
            return false;
        }
        int meatCount = meatYield(fish);
        inputContainer.setItem(0, ItemStack.EMPTY);
        grantMeat(player, meatCount);
        announce(player, meatCount);
        return true;
    }

    private boolean cleanAll(ServerPlayer player) {
        int totalMeat = 0;

        ItemStack slotFish = inputContainer.getItem(0);
        if (!slotFish.isEmpty() && CatchBagContents.isFish(slotFish)) {
            totalMeat += meatYield(slotFish);
            inputContainer.setItem(0, ItemStack.EMPTY);
        }

        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (CatchBagContents.isFish(stack)) {
                totalMeat += meatYield(stack);
                inventory.setItem(i, ItemStack.EMPTY);
            } else if (stack.getItem() instanceof CatchBagItem) {
                CatchBagContents contents = stack.getOrDefault(ModItems.CATCH_BAG_CONTENTS, CatchBagContents.EMPTY);
                if (contents.isEmpty()) {
                    continue;
                }
                for (ItemStack fishInBag : contents.stacks()) {
                    totalMeat += meatYield(fishInBag);
                }
                stack.set(ModItems.CATCH_BAG_CONTENTS, CatchBagContents.EMPTY);
            }
        }

        if (totalMeat == 0) {
            player.sendSystemMessage(Component.literal("You have no fish to clean."));
            return false;
        }
        grantMeat(player, totalMeat);
        announce(player, totalMeat);
        return true;
    }

    /** 1 Fish Meat per 5 lb of the fish's weight, minimum 1, scaled by the stack's own count. */
    private static int meatYield(ItemStack fishStack) {
        FishWeight weight = fishStack.get(ModItems.FISH_WEIGHT.get());
        if (weight == null) {
            return 0;
        }
        return Math.max(1, (int) (weight.pounds() / 5.0)) * fishStack.getCount();
    }

    private static void grantMeat(ServerPlayer player, int meatCount) {
        int remaining = meatCount;
        int maxStackSize = ModItems.FISH_MEAT.get().getDefaultMaxStackSize();
        while (remaining > 0) {
            int chunk = Math.min(remaining, maxStackSize);
            ItemStack meat = new ItemStack(ModItems.FISH_MEAT.get(), chunk);
            if (!player.getInventory().add(meat)) {
                player.drop(meat, false);
            }
            remaining -= chunk;
        }
    }

    private static void announce(ServerPlayer player, int meatCount) {
        player.level().playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.BLOCKS, 1.0F, 1.0F);
        player.sendSystemMessage(Component.literal("You clean the fish, yielding " + meatCount + " Fish Meat."));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        ItemStack clicked = ItemStack.EMPTY;
        Slot slot = slots.get(slotIndex);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            clicked = stack.copy();
            if (slotIndex == 0) {
                if (!moveItemStackTo(stack, 1, slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(stack, 0, 1, false)) {
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return clicked;
    }

    @Override
    public boolean stillValid(Player player) {
        return inputContainer.stillValid(player);
    }
}
