package com.github.cerealklla.lyfe.fishing;

import java.util.List;

import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

/**
 * Catch Bag's own tooltip-image data (2026-10-03 playtest fix). Deliberately NOT vanilla's own
 * {@code BundleTooltip}/{@code BundleContents} -- that type's progress bar is computed from real
 * item-stack counts relative to each item's own max stack size, which has nothing to do with this
 * bag's actual pounds cap (confirmed live: a 93.9/100 lb bag rendered an almost-empty vanilla bar
 * since only 7 stacks sat against a typical item's 64-count max). {@code
 * fishing.client.ClientCatchBagTooltip} renders the same grid-of-icons layout as vanilla's Bundle
 * (reusing its real sprites), but drives the progress bar off {@code totalPounds/maxPounds} directly.
 */
public record CatchBagTooltip(List<ItemStack> stacks, double totalPounds, double maxPounds) implements TooltipComponent {
}
