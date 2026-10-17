package com.github.cerealklla.lyfe.socket.client;

import com.github.cerealklla.lyfe.socket.SocketData;
import com.github.cerealklla.lyfe.socket.SocketeerConstants;
import com.github.cerealklla.lyfe.socket.SocketingMenu;
import com.github.cerealklla.lyfe.registration.ModItems;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** Visual style directly copied from {@code repair.client.RepairStructureScreen} (beveled flat panel, no texture file). */
public class SocketingScreen extends AbstractContainerScreen<SocketingMenu> {

    private static final int BUTTON_WIDTH = 150;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 4;
    private static final int BUTTONS_TOP_Y = 76;
    private static final int STATUS_LABEL_Y = 66;

    private static final int PANEL_BODY_COLOR = 0xFFC6C6C6;
    private static final int PANEL_OUTER_BORDER_COLOR = 0xFF373737;
    private static final int PANEL_HIGHLIGHT_COLOR = 0xFFFFFFFF;
    private static final int PANEL_SHADOW_COLOR = 0xFF8B8B8B;

    public SocketingScreen(SocketingMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, SocketingMenu.PLAYER_INV_Y + 76 + 6);
        this.titleLabelX = 10;
        this.titleLabelY = 8;
        this.inventoryLabelY = SocketingMenu.PLAYER_INV_Y - 10;
    }

    @Override
    protected void init() {
        super.init();
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;
        int buttonX = x + (imageWidth - BUTTON_WIDTH) / 2;

        addRenderableWidget(Button.builder(Component.literal("Drill Socket"),
                        button -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, SocketingMenu.DRILL_BUTTON_ID))
                .bounds(buttonX, y + BUTTONS_TOP_Y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Insert"),
                        button -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, SocketingMenu.INSERT_BUTTON_ID))
                .bounds(buttonX, y + BUTTONS_TOP_Y + BUTTON_HEIGHT + BUTTON_GAP, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);
        ItemStack target = menu.targetItem();
        if (target.isEmpty()) {
            return;
        }
        SocketData data = target.getOrDefault(ModItems.SOCKET_DATA.get(), SocketData.EMPTY);
        String line;
        if (data.isFull()) {
            line = "Sockets: " + data.socketCount() + "/" + SocketData.MAX_SOCKETS + " (full)";
        } else {
            int requirement = SocketeerConstants.socketRequirement(data.socketCount());
            line = "Sockets: " + data.socketCount() + "/" + SocketData.MAX_SOCKETS
                    + "  Progress: " + data.progressTowardNextSocket() + "/" + requirement;
        }
        int textWidth = font.width(line);
        graphics.text(font, line, (imageWidth - textWidth) / 2, STATUS_LABEL_Y, 0xFF404040, false);
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
