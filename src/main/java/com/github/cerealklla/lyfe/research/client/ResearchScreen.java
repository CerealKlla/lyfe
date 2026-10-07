package com.github.cerealklla.lyfe.research.client;

import com.github.cerealklla.lyfe.research.ResearchMenu;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * The Research Bench's screen -- one input slot, a "Research" button, player inventory.
 * **Revised 2026-10-02**: no longer borrows vanilla's `generic_54` chest texture (sized for 9 slots
 * per row while this screen only has 1 real slot -- that left 8 "ghost" slots and a visible gap
 * before the inventory panel). **Restyled again same day**, matching {@code
 * CraftingStructureScreen}'s own pass: vanilla's classic light-gray beveled panel instead of a flat
 * dark custom theme, and a real drawn slot backdrop behind the one true input slot.
 */
public class ResearchScreen extends AbstractContainerScreen<ResearchMenu> {

    // Must match ResearchMenu.PLAYER_INV_Y -- tightened 2026-10-02 (the slot/button row moved up,
    // user report: too much dead space, and the slot/button were misaligned against each other).
    private static final int PLAYER_INV_Y = 66;

    private static final int PANEL_BODY_COLOR = 0xFFC6C6C6;
    private static final int PANEL_OUTER_BORDER_COLOR = 0xFF373737;
    private static final int PANEL_HIGHLIGHT_COLOR = 0xFFFFFFFF;
    private static final int PANEL_SHADOW_COLOR = 0xFF8B8B8B;

    public ResearchScreen(ResearchMenu menu, Inventory inventory, Component title) {
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
        // Centered against ResearchMenu's real input slot (local 16,34 / 16x16 -> center y 42):
        // button height 20 centered on that same y means a y offset of 42-10=32.
        addRenderableWidget(Button.builder(Component.literal("Research"),
                        button -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, ResearchMenu.RESEARCH_BUTTON_ID))
                .bounds(x + 48, y + 32, 100, 20)
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
