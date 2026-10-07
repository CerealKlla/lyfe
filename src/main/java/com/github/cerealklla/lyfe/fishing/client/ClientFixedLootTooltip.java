package com.github.cerealklla.lyfe.fishing.client;

import java.util.List;

import com.github.cerealklla.lyfe.fishing.FixedLootTooltip;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * See {@link FixedLootTooltip}. Same grid layout/sprites as {@link ClientCatchBagTooltip}, minus the
 * progress bar -- a fixed loot payload has nothing for a bar to represent.
 */
public final class ClientFixedLootTooltip implements ClientTooltipComponent {

    private static final Identifier SLOT_BACKGROUND_SPRITE = Identifier.withDefaultNamespace("container/bundle/slot_background");
    private static final int SLOT_SIZE = 24;
    private static final int GRID_WIDTH = 96;
    private static final int MAX_SHOWN_ITEMS = 12;

    private final FixedLootTooltip tooltip;

    public ClientFixedLootTooltip(FixedLootTooltip tooltip) {
        this.tooltip = tooltip;
    }

    @Override
    public int getWidth(Font font) {
        return GRID_WIDTH;
    }

    @Override
    public int getHeight(Font font) {
        return itemGridHeight();
    }

    @Override
    public boolean showTooltipWithItemInHand() {
        return true;
    }

    private int gridSizeY() {
        return Mth.positiveCeilDiv(Math.min(MAX_SHOWN_ITEMS, tooltip.stacks().size()), 4);
    }

    private int itemGridHeight() {
        return gridSizeY() * SLOT_SIZE;
    }

    private static int getContentXOffset(int tooltipWidth) {
        return (tooltipWidth - GRID_WIDTH) / 2;
    }

    private int getNumberOfItemsToShow() {
        int numberOfItemStacks = tooltip.stacks().size();
        int availableItemsToShow = numberOfItemStacks > MAX_SHOWN_ITEMS ? 11 : MAX_SHOWN_ITEMS;
        int itemsOnNonFullRow = numberOfItemStacks % 4;
        int emptySpaceOnNonFullRow = itemsOnNonFullRow == 0 ? 0 : 4 - itemsOnNonFullRow;
        return Math.min(numberOfItemStacks, availableItemsToShow - emptySpaceOnNonFullRow);
    }

    private List<ItemStack> getShownItems() {
        int lastToDisplay = Math.min(tooltip.stacks().size(), getNumberOfItemsToShow());
        return tooltip.stacks().subList(0, lastToDisplay);
    }

    @Override
    public void extractImage(Font font, int x, int y, int w, int h, GuiGraphicsExtractor graphics) {
        boolean isOverflowing = tooltip.stacks().size() > MAX_SHOWN_ITEMS;
        List<ItemStack> shownItems = getShownItems();
        int xStartPos = x + getContentXOffset(w) + GRID_WIDTH;
        int yStartPos = y + gridSizeY() * SLOT_SIZE;
        int slotNumber = 1;

        for (int rowNumber = 1; rowNumber <= gridSizeY(); rowNumber++) {
            for (int columnNumber = 1; columnNumber <= 4; columnNumber++) {
                int drawX = xStartPos - columnNumber * SLOT_SIZE;
                int drawY = yStartPos - rowNumber * SLOT_SIZE;
                if (isOverflowing && columnNumber * rowNumber == 1) {
                    int hidden = tooltip.stacks().size() - shownItems.size();
                    graphics.centeredText(font, "+" + hidden, drawX + 12, drawY + 10, -1);
                } else if (shownItems.size() >= slotNumber) {
                    int itemVisualOrderIndex = shownItems.size() - slotNumber;
                    ItemStack item = shownItems.get(itemVisualOrderIndex);
                    graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_BACKGROUND_SPRITE, drawX, drawY, SLOT_SIZE, SLOT_SIZE);
                    graphics.item(item, drawX + 4, drawY + 4, slotNumber);
                    graphics.itemDecorations(font, item, drawX + 4, drawY + 4);
                    slotNumber++;
                }
            }
        }
    }
}
