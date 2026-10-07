package com.github.cerealklla.lyfe.fishing;

import java.util.List;

import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

/**
 * Tooltip-image data for {@link FixedLootContents}-backed items (Sunken Treasure Bag, Locked Chest
 * variants) -- same grid-of-icons idea as {@link CatchBagTooltip}, but with no progress bar: a fixed
 * loot payload has no capacity/weight cap to show a fraction of, just a list that shrinks to empty.
 */
public record FixedLootTooltip(List<ItemStack> stacks) implements TooltipComponent {
}
