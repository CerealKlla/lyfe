package com.github.cerealklla.lyfe.repair;

import com.github.cerealklla.lyfe.structure.FundingOption;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Replaces vanilla's Anvil/Grindstone menu entirely (2026-10-07 user request: "the existing
 * [vanilla] structure(s) used for repairing... will now have a repair GUI") -- one slot for the
 * damaged item, and the same 3 funding-option buttons {@code craft.CraftingStructureMenu}'s own
 * "Upgrade Station" already uses ({@link FundingOption}), reworded to "Repair with..." per the
 * user's exact spec. See {@link RepairFunding} for what each option actually costs and {@link
 * RepairInteractionListener} for how a right-click on the real Anvil/Grindstone block ends up here
 * instead of vanilla's own menu.
 *
 * <p>The repaired item stays in its own slot afterward (just with its damage cleared) rather than
 * being auto-collected anywhere -- the player takes it back out themselves, same as placing it in.
 */
public class RepairStructureMenu extends AbstractContainerMenu {

    public static final int REPAIR_RESOURCES_BUTTON_ID = 0;
    public static final int REPAIR_MIX_BUTTON_ID = 1;
    public static final int REPAIR_GOLD_BUTTON_ID = 2;

    private static final int INPUT_SLOT_X = 80;
    private static final int INPUT_SLOT_Y = 20;

    // Public: RepairStructureScreen positions its 3 stacked buttons in the gap above this same value,
    // so there is exactly one source of truth for where the inventory row starts -- a real bug found
    // live, 2026-10-07 (two separately-hardcoded copies of this had drifted apart, so the buttons and
    // the inventory slots visibly overlapped).
    public static final int PLAYER_INV_Y = 140;

    private final Container inputContainer = new SimpleContainer(1);
    private final ContainerLevelAccess access;

    public RepairStructureMenu(MenuType<?> type, int containerId, Inventory inventory, ContainerLevelAccess access) {
        super(type, containerId);
        this.access = access;
        layoutSlots(inventory);
    }

    /** Client-side reconstruction (see {@code registration.ModMenus}) -- no real block position to read from. */
    public RepairStructureMenu(MenuType<?> type, int containerId, Inventory inventory) {
        this(type, containerId, inventory, ContainerLevelAccess.NULL);
    }

    private void layoutSlots(Inventory inventory) {
        addSlot(new Slot(inputContainer, 0, INPUT_SLOT_X, INPUT_SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return RepairableItem.of(stack) != null;
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

    /** The item currently in the repair slot (possibly empty) -- {@code client.RepairStructureScreen} reads this each frame to preview the repair cost. */
    public ItemStack repairInput() {
        return inputContainer.getItem(0);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        FundingOption option = switch (id) {
            case REPAIR_RESOURCES_BUTTON_ID -> FundingOption.ON_HAND;
            case REPAIR_MIX_BUTTON_ID -> FundingOption.MIX;
            case REPAIR_GOLD_BUTTON_ID -> FundingOption.GOLD_ONLY;
            default -> null;
        };
        if (option == null || !(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        ItemStack stack = inputContainer.getItem(0);
        RepairFunding.Result result = access.evaluate((level, pos) -> {
            if (!(level instanceof ServerLevel serverLevel)) {
                return RepairFunding.Result.fail("Nothing to repair here.");
            }
            return RepairFunding.repair(serverPlayer, serverLevel, pos, stack, option);
        }, RepairFunding.Result.fail("Nothing to repair here."));

        if (!result.success()) {
            player.sendSystemMessage(Component.literal(result.message()));
            return false;
        }
        inputContainer.setChanged();
        player.sendSystemMessage(Component.literal("Repaired!"));
        return true;
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
        access.execute((level, pos) -> clearContainer(player, inputContainer));
    }

    @Override
    public boolean stillValid(Player player) {
        return access.evaluate((level, pos) ->
                player.level() == level && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0, true);
    }
}
