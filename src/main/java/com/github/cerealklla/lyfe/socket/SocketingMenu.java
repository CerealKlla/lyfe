package com.github.cerealklla.lyfe.socket;

import java.util.Random;

import com.github.cerealklla.lyfe.api.Lyfe;
import com.github.cerealklla.lyfe.registration.ModItems;
import com.github.cerealklla.lyfe.skill.Skills;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The Die Grinder's GUI (explicit user spec) -- one input slot, direct mirror of {@code
 * repair.RepairStructureMenu}. {@link #DRILL_BUTTON_ID} is the only action here: "add a socket" (the
 * real described mechanic -- progress/durability/A-D outcome roll against the Die Grinder currently
 * in the player's hand). Inserting a Gem/Rune into an already-drilled socket is NOT done here at all
 * -- see {@link SocketScoopHandler}'s own doc for the real mechanic (explicit user correction,
 * 2026-10-18: pick up the Gem/Rune and click it directly onto the socketable item in any inventory
 * screen, mirroring vanilla's own Bundle interaction -- no Die Grinder needed for that half at all).
 */
public class SocketingMenu extends AbstractContainerMenu {

    public static final int DRILL_BUTTON_ID = 0;

    private static final int TARGET_SLOT_X = 80;
    private static final int TARGET_SLOT_Y = 20;
    public static final int PLAYER_INV_Y = 140;

    private final Container container = new SimpleContainer(1);
    private final Random random = new Random();

    public SocketingMenu(MenuType<?> type, int containerId, Inventory inventory) {
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
        if (id != DRILL_BUTTON_ID || !(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        return attemptDrill(serverPlayer);
    }

    private boolean attemptDrill(ServerPlayer player) {
        ItemStack target = container.getItem(0);
        if (EquipmentCategory.of(target) == null) {
            player.sendSystemMessage(Component.literal("Place an item to work on first."));
            return false;
        }
        ItemStack grinder = findDieGrinder(player);
        if (grinder.isEmpty()) {
            player.sendSystemMessage(Component.literal("You need the Diamond Die Grinder in hand."));
            return false;
        }
        SocketData data = target.getOrDefault(ModItems.SOCKET_DATA.get(), SocketData.EMPTY);
        if (data.isFull()) {
            player.sendSystemMessage(Component.literal("This item already has the maximum 5 sockets."));
            return false;
        }

        int socketIndex = data.socketCount();
        int requirement = SocketeerConstants.socketRequirement(socketIndex);
        int socketeerLevel = Lyfe.getLevel(player, Skills.SOCKETEER_ID);
        SocketeerConstants.SocketOutcome outcome = SocketeerConstants.rollSocketOutcome(socketeerLevel, random);
        int durabilityCost = SocketeerConstants.durabilityCostFor(outcome);
        int progressGained = SocketeerConstants.progressGrantedFor(outcome);

        int newProgress = data.progressTowardNextSocket() + progressGained;
        SocketData newData;
        String message;
        if (newProgress >= requirement) {
            newData = data.withNewEmptySocket();
            message = "Socket drilled! (" + newData.socketCount() + "/" + SocketData.MAX_SOCKETS + ")";
        } else {
            newData = data.withProgress(newProgress);
            int pct = requirement <= 0 ? 100 : (int) (100.0 * newProgress / requirement);
            message = switch (outcome) {
                case PROGRESS_HALF_DAMAGE, PROGRESS_FULL_DAMAGE -> "Progress made (" + pct + "%).";
                case NO_PROGRESS_FULL_DAMAGE -> "No progress this time.";
                case NO_PROGRESS_DOUBLE_DAMAGE -> "No progress -- and the grinder slipped (double wear)!";
            };
        }
        target.set(ModItems.SOCKET_DATA.get(), newData);
        SocketApplier.recompute(target);

        grinder.hurtAndBreak(durabilityCost, player, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        Lyfe.addXp(player, Skills.SOCKETEER_ID, durabilityCost);

        player.sendSystemMessage(Component.literal(message));
        container.setChanged();
        return true;
    }

    private static ItemStack findDieGrinder(ServerPlayer player) {
        if (SocketToolRegistry.isDieGrinder(player.getMainHandItem().getItem())) {
            return player.getMainHandItem();
        }
        if (SocketToolRegistry.isDieGrinder(player.getOffhandItem().getItem())) {
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
