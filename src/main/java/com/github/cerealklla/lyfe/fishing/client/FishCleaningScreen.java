package com.github.cerealklla.lyfe.fishing.client;

import com.github.cerealklla.lyfe.fishing.FishCleaningMenu;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * The Fish Cleaning Station's screen (2026-10-05) -- one input slot, "Clean Single Fish"/"Clean
 * All Fish" buttons, player inventory. Direct structural copy of {@code research.client.ResearchScreen}'s
 * styling (vanilla's classic beveled panel), just with two stacked buttons instead of one.
 */
public class FishCleaningScreen extends AbstractContainerScreen<FishCleaningMenu> {

    // Must match FishCleaningMenu.PLAYER_INV_Y -- bumped 66 -> 86, 2026-10-05 playtest fix (the
    // "Inventory" label was overlapping the bottom of the "Clean All Fish" button).
    private static final int PLAYER_INV_Y = 86;

    private static final int PANEL_BODY_COLOR = 0xFFC6C6C6;
    private static final int PANEL_OUTER_BORDER_COLOR = 0xFF373737;
    private static final int PANEL_HIGHLIGHT_COLOR = 0xFFFFFFFF;
    private static final int PANEL_SHADOW_COLOR = 0xFF8B8B8B;

    public FishCleaningScreen(FishCleaningMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, PLAYER_INV_Y + 76 + 6);
        this.titleLabelX = 10;
        this.titleLabelY = 8;
        this.inventoryLabelY = PLAYER_INV_Y - 10;
    }

    @Override
    protected void init() {
        super.init();
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;
        // Centered against the real input slot (local 16,34 / 16x16 -> center y 42), same alignment
        // math ResearchScreen's own single button uses, just split across two stacked rows.
        addRenderableWidget(Button.builder(Component.literal("Clean Single Fish"),
                        button -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, FishCleaningMenu.CLEAN_SINGLE_BUTTON_ID))
                .bounds(x + 48, y + 22, 120, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Clean All Fish"),
                        button -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, FishCleaningMenu.CLEAN_ALL_BUTTON_ID))
                .bounds(x + 48, y + 44, 120, 20)
                .build());
    }

    private void drawSlotBackdrop(GuiGraphicsExtractor graphics, int xo, int yo, int localX, int localY) {
        int x = xo + localX;
        int y = yo + localY;
        graphics.fill(x - 1, y - 1, x + 17, y + 17, PANEL_OUTER_BORDER_COLOR);
        graphics.fill(x, y, x + 16, y + 16, PANEL_SHADOW_COLOR);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int xo = (this.width - this.imageWidth) / 2;
        int yo = (this.height - this.imageHeight) / 2;

        graphics.fill(xo, yo, xo + imageWidth, yo + imageHeight, PANEL_OUTER_BORDER_COLOR);
        graphics.fill(xo + 1, yo + 1, xo + imageWidth - 1, yo + imageHeight - 1, PANEL_HIGHLIGHT_COLOR);
        graphics.fill(xo + 2, yo + 2, xo + imageWidth - 1, yo + imageHeight - 1, PANEL_SHADOW_COLOR);
        graphics.fill(xo + 2, yo + 2, xo + imageWidth - 2, yo + imageHeight - 2, PANEL_BODY_COLOR);

        for (var slot : menu.slots) {
            drawSlotBackdrop(graphics, xo, yo, slot.x, slot.y);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
