package com.github.cerealklla.lyfe.structure;

import java.util.List;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * Counts/drains {@link UpgradeCostEntry}s across a list of containers -- the "on-hand plot
 * resources" half of the upgrade-cost feature (2026-10-05). Same shape as Yconomics'
 * {@code shop.ShopTransferEngine} (a tag-aware, single-resource-at-a-time drain), independently
 * written here rather than shared -- this suite's established precedent for small cross-mod-
 * duplicated helpers (see {@code founding.GhostBlockDisplays}' own doc in Settlemynts).
 */
public final class StructureCostTransfer {

    private StructureCostTransfer() {
    }

    public static int countAvailable(List<Container> containers, UpgradeCostEntry entry) {
        int total = 0;
        for (Container container : containers) {
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack stack = container.getItem(slot);
                if (entry.matches(stack)) {
                    total += stack.getCount();
                }
            }
        }
        return total;
    }

    /** Removes up to {@code amount} units of {@code entry} from {@code containers} (list order). Returns the amount actually removed, which may be less than {@code amount}. */
    public static int drain(List<Container> containers, UpgradeCostEntry entry, int amount) {
        int remaining = amount;
        for (Container container : containers) {
            if (remaining <= 0) {
                break;
            }
            for (int slot = 0; slot < container.getContainerSize() && remaining > 0; slot++) {
                ItemStack stack = container.getItem(slot);
                if (!entry.matches(stack)) {
                    continue;
                }
                int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                remaining -= take;
            }
        }
        return amount - remaining;
    }
}
