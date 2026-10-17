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
 * The Die Grinder's GUI (explicit user spec) -- two input slots, mirroring {@code
 * repair.RepairStructureMenu}'s one-slot shape exactly, widened to two: slot 0 is the equipment
 * being worked on, slot 1 is a Gem or Rune to insert into an already-drilled empty socket. Two
 * independent actions, neither specified by the user as needing its own roll/chance beyond what was
 * explicitly described for drilling:
 *
 * <ul>
 *   <li>{@link #DRILL_BUTTON_ID} -- "add a socket" (the real described mechanic: progress/durability/
 *   A-D outcome roll against the Die Grinder currently in the player's hand).</li>
 *   <li>{@link #INSERT_BUTTON_ID} -- filling an already-empty socket with slot 1's Gem/Rune. The user
 *   never specified an insertion mechanic at all (only drilling and extracting had described rolls),
 *   so this is deliberately simple/deterministic: consume the Gem/Rune, fill the first empty socket,
 *   recompute the item's attribute bonuses. Flagged as a filled-in gap, not guessed at randomly.</li>
 * </ul>
 */
public class SocketingMenu extends AbstractContainerMenu {

    public static final int DRILL_BUTTON_ID = 0;
    public static final int INSERT_BUTTON_ID = 1;

    private static final int TARGET_SLOT_X = 70;
    private static final int TARGET_SLOT_Y = 20;
    private static final int INSERT_SLOT_X = 70;
    private static final int INSERT_SLOT_Y = 50;

    public static final int PLAYER_INV_Y = 140;

    private final Container container = new SimpleContainer(2);
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
        addSlot(new Slot(container, 1, INSERT_SLOT_X, INSERT_SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return GemItem.gemTypeOf(stack) != null || stack.is(ModItems.RUNE.get());
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

    public ItemStack insertItem() {
        return container.getItem(1);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        if (id == DRILL_BUTTON_ID) {
            return attemptDrill(serverPlayer);
        }
        if (id == INSERT_BUTTON_ID) {
            return attemptInsert(serverPlayer);
        }
        return false;
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

    private boolean attemptInsert(ServerPlayer player) {
        ItemStack target = container.getItem(0);
        ItemStack toInsert = container.getItem(1);
        if (EquipmentCategory.of(target) == null || toInsert.isEmpty()) {
            player.sendSystemMessage(Component.literal("Place an item and a Gem or Rune to insert."));
            return false;
        }
        SocketData data = target.getOrDefault(ModItems.SOCKET_DATA.get(), SocketData.EMPTY);
        int emptyIndex = data.firstEmptyIndex();
        if (emptyIndex < 0) {
            player.sendSystemMessage(Component.literal("No empty socket to insert into."));
            return false;
        }

        SocketSlot slot;
        GemType gemType = GemItem.gemTypeOf(toInsert);
        if (gemType != null) {
            GemQuality quality = GemItem.gemQualityOf(toInsert);
            slot = SocketSlot.ofGem(gemType, quality);
        } else if (toInsert.is(ModItems.RUNE.get())) {
            RuneId runeId = RuneItem.runeIdOf(toInsert);
            if (runeId == null) {
                return false;
            }
            slot = SocketSlot.ofRune(runeId);
        } else {
            player.sendSystemMessage(Component.literal("That isn't a Gem or Rune."));
            return false;
        }

        target.set(ModItems.SOCKET_DATA.get(), data.withSlot(emptyIndex, slot));
        SocketApplier.recompute(target);
        toInsert.shrink(1);
        container.setChanged();
        player.sendSystemMessage(Component.literal("Inserted."));
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
            int containerSize = 2;
            if (slotIndex < containerSize) {
                if (!moveItemStackTo(stack, containerSize, slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (EquipmentCategory.of(stack) != null && !moveItemStackTo(stack, 0, 1, false)) {
                if (!moveItemStackTo(stack, 1, 2, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(stack, 1, 2, false) && !moveItemStackTo(stack, 0, 1, false)) {
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
