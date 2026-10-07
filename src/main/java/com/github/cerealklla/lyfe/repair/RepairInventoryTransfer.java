package com.github.cerealklla.lyfe.repair;

import com.github.cerealklla.lyfe.structure.UpgradeCostEntry;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Counts/drains an {@link UpgradeCostEntry} across a player's own carried inventory -- the "on-hand"
 * half of repair funding (repair's own materials come from the player directly, not a plot's boxes,
 * unlike {@code structure.StructureCostTransfer}). Scoped to the 27 main-inventory slots only (9-35),
 * same convention as {@code craft.CraftingStructureMenu#MAIN_INVENTORY_START/END} -- never the
 * hotbar or equipped armor/offhand, so repairing gear can't accidentally eat the very item in your
 * hand or the armor you're wearing.
 */
final class RepairInventoryTransfer {

    private static final int MAIN_INVENTORY_START = 9;
    private static final int MAIN_INVENTORY_END = 36;

    private RepairInventoryTransfer() {
    }

    static int countAvailable(ServerPlayer player, UpgradeCostEntry entry) {
        int total = 0;
        Inventory inventory = player.getInventory();
        for (int i = MAIN_INVENTORY_START; i < MAIN_INVENTORY_END; i++) {
            ItemStack stack = inventory.getItem(i);
            if (entry.matches(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** Removes up to {@code amount} units of {@code entry} from the player's main inventory. Returns the amount actually removed. */
    static int drain(ServerPlayer player, UpgradeCostEntry entry, int amount) {
        int remaining = amount;
        Inventory inventory = player.getInventory();
        for (int i = MAIN_INVENTORY_START; i < MAIN_INVENTORY_END && remaining > 0; i++) {
            ItemStack stack = inventory.getItem(i);
            if (!entry.matches(stack)) {
                continue;
            }
            int take = Math.min(remaining, stack.getCount());
            stack.shrink(take);
            remaining -= take;
        }
        return amount - remaining;
    }
}
