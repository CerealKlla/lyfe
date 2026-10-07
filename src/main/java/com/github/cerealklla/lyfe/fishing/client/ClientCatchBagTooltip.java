package com.github.cerealklla.lyfe.fishing.client;

import java.util.List;
import java.util.Locale;

import com.github.cerealklla.lyfe.fishing.CatchBagTooltip;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * See {@link CatchBagTooltip}. A structural copy of vanilla's own {@code ClientBundleTooltip} grid
 * layout and sprite set (reused directly, no new art), minus the selected-item/highlight sub-feature
 * this bag has no equivalent interaction for, and with the progress bar driven by real pounds
 * instead of vanilla's item-count-based {@code Fraction}. Client-only by virtue of living in this
 * {@code .client} package and only ever being referenced from {@code LyfeModClient} (itself
 * {@code Dist.CLIENT}-gated) -- no {@code @OnlyIn} here, matching every other client-only class in
 * this mod; NeoForge flagged it as an error on launch ("runtime member-stripping behaviour of this
 * annotation is no longer present"), the only class in the whole mod that had it.
 */
public final class ClientCatchBagTooltip implements ClientTooltipComponent {

    private static final Identifier PROGRESSBAR_BORDER_SPRITE = Identifier.withDefaultNamespace("container/bundle/bundle_progressbar_border");
    private static final Identifier PROGRESSBAR_FILL_SPRITE = Identifier.withDefaultNamespace("container/bundle/bundle_progressbar_fill");
    private static final Identifier PROGRESSBAR_FULL_SPRITE = Identifier.withDefaultNamespace("container/bundle/bundle_progressbar_full");
    private static final Identifier SLOT_BACKGROUND_SPRITE = Identifier.withDefaultNamespace("container/bundle/slot_background");
    private static final int SLOT_SIZE = 24;
    private static final int GRID_WIDTH = 96;
    private static final int MAX_SHOWN_ITEMS = 12;

    private final CatchBagTooltip tooltip;

    public ClientCatchBagTooltip(CatchBagTooltip tooltip) {
        this.tooltip = tooltip;
    }

    @Override
    public int getWidth(Font font) {
        return GRID_WIDTH;
    }

    @Override
    public int getHeight(Font font) {
        return itemGridHeight() + 13 + 8;
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

    /** Mirrors {@code BundleContents#getNumberOfItemsToShow} exactly -- how many real item slots to
     * draw, snapped to full rows, leaving one cell free for a "+N" overflow count when needed. */
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

        extractProgressbar(x + getContentXOffset(w), y + itemGridHeight() + 4, font, graphics);
    }

    private void extractProgressbar(int x, int y, Font font, GuiGraphicsExtractor graphics) {
        double fraction = tooltip.maxPounds() <= 0.0
                ? 0.0
                : Mth.clamp(tooltip.totalPounds() / tooltip.maxPounds(), 0.0, 1.0);
        int fill = Mth.clamp((int) Math.round(fraction * 94.0), 0, 94);
        Identifier texture = fraction >= 1.0 ? PROGRESSBAR_FULL_SPRITE : PROGRESSBAR_FILL_SPRITE;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, texture, x + 1, y, fill, 13);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, PROGRESSBAR_BORDER_SPRITE, x, y, 96, 13);
        Component text = Component.literal(String.format(Locale.ROOT, "%.1f / %.0f lb",
                tooltip.totalPounds(), tooltip.maxPounds()));
        graphics.centeredText(font, text, x + 48, y + 3, -1);
    }
}
