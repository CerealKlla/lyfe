package com.github.cerealklla.lyfe.fishing;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The Fish Cleaning Station's one input slot -- only accepts items carrying a {@link FishWeight}
 * component. Vanilla's own {@code Slot#mayPlace} defaults to unconditionally {@code true} and never
 * consults {@code Container#canPlaceItem} on its own (the same real bug Settlemynts'
 * {@code GuardhouseFoodSlot} was built to fix), so this dedicated subclass is the actual
 * enforcement point.
 */
public class FishOnlySlot extends Slot {

    public FishOnlySlot(Container container, int index, int x, int y) {
        super(container, index, x, y);
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return CatchBagContents.isFish(stack);
    }
}
