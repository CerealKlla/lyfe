package com.github.cerealklla.lyfe.socket;

import java.util.Random;

import com.github.cerealklla.lyfe.api.Lyfe;
import com.github.cerealklla.lyfe.registration.ModAttachments;
import com.github.cerealklla.lyfe.registration.ModItems;
import com.github.cerealklla.lyfe.skill.Skills;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** The Pry Bar's GUI (explicit user spec) -- one input slot, direct mirror of {@code repair.RepairStructureMenu}. */
public class ExtractingMenu extends AbstractContainerMenu {

    public static final int EXTRACT_BUTTON_ID = 0;

    private static final int TARGET_SLOT_X = 80;
    private static final int TARGET_SLOT_Y = 20;
    public static final int PLAYER_INV_Y = 140;

    private final Container container = new SimpleContainer(1);
    private final Random random = new Random();

    public ExtractingMenu(MenuType<?> type, int containerId, Inventory inventory) {
        super(type, containerId);
        layoutSlots(inventory);
    }

    private void layoutSlots(Inventory inventory) {
        addSlot(new Slot(container, 0, TARGET_SLOT_X, TARGET_SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return EquipmentCategory.of(stack) != null;
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, PLAYER_INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, PLAYER_INV_Y + 58));
        }
    }

    public ItemStack targetItem() {
        return container.getItem(0);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id != EXTRACT_BUTTON_ID || !(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        return attemptExtract(serverPlayer);
    }

    private boolean attemptExtract(ServerPlayer player) {
        ItemStack target = container.getItem(0);
        if (EquipmentCategory.of(target) == null) {
            player.sendSystemMessage(Component.literal("Place an item to extract from first."));
            return false;
        }
        ItemStack pryBar = findPryBar(player);
        if (pryBar.isEmpty()) {
            player.sendSystemMessage(Component.literal("You need the Diamond Pry Bar in hand."));
            return false;
        }
        SocketData data = target.getOrDefault(ModItems.SOCKET_DATA.get(), SocketData.EMPTY);
        int lastFilled = data.lastFilledIndex();
        if (lastFilled < 0) {
            player.sendSystemMessage(Component.literal("This item has no gems or runes to extract."));
            return false;
        }

        SocketSlot slot = data.sockets().get(lastFilled);
        boolean isRune = slot.kind() == SocketSlot.Kind.RUNE;
        int socketeerLevel = Lyfe.getLevel(player, Skills.SOCKETEER_ID);
        SocketeerConstants.ExtractOutcome outcome = SocketeerConstants.rollExtractOutcome(socketeerLevel, isRune, random);

        String message = switch (outcome) {
            case SUCCESS -> {
                ItemStack returned = isRune
                        ? RuneItem.create(slot.runeId().orElseThrow())
                        : GemItem.create(slot.gemType().orElseThrow(), slot.gemQuality().orElseThrow());
                if (!player.getInventory().add(returned)) {
                    player.drop(returned, false);
                }
                // Real report, 2026-10-17: extracting a Rune never recorded it in the Rune Codex --
                // this path hands the stack straight into the inventory, never spawning a real
                // ItemEntity, so socket.SocketDiscoveryListener's ItemEntityPickupEvent hook (the
                // only other place this is recorded) never sees it.
                if (isRune && SocketDiscoveryListener.recordDiscoveredRunes(player, returned)) {
                    player.syncData(ModAttachments.PLAYER_DISCOVERED_RUNES);
                }
                yield "Extraction successful!";
            }
            case DEGRADE -> {
                GemQuality current = slot.gemQuality().orElseThrow();
                GemQuality degraded = current.index() > 0 ? GemQuality.values()[current.index() - 1] : current;
                ItemStack returned = GemItem.create(slot.gemType().orElseThrow(), degraded);
                if (!player.getInventory().add(returned)) {
                    player.drop(returned, false);
                }
                yield "The gem's quality degraded to " + degraded.displayName() + ".";
            }
            case DESTROY -> "It was destroyed in the attempt.";
        };

        target.set(ModItems.SOCKET_DATA.get(), data.withSlotEmptied(lastFilled));
        SocketApplier.recompute(target);

        pryBar.hurtAndBreak(SocketeerConstants.EXTRACT_DURABILITY_COST, player, EquipmentSlot.MAINHAND);
        Lyfe.addXp(player, Skills.SOCKETEER_ID, SocketeerConstants.EXTRACT_DURABILITY_COST);

        player.sendSystemMessage(Component.literal(message));
        container.setChanged();
        return true;
    }

    private static ItemStack findPryBar(ServerPlayer player) {
        if (SocketToolRegistry.isPryBar(player.getMainHandItem().getItem())) {
            return player.getMainHandItem();
        }
        if (SocketToolRegistry.isPryBar(player.getOffhandItem().getItem())) {
            return player.getOffhandItem();
        }
        return ItemStack.EMPTY;
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
    public void removed(Player player) {
        super.removed(player);
        clearContainer(player, container);
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
