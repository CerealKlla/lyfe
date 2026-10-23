package com.github.cerealklla.lyfe.summoning.client;

import com.github.cerealklla.lyfe.summoning.AllayBottleMenu;
import com.github.cerealklla.lyfe.summoning.RenameAllayBottlePayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.world.entity.player.Inventory;

/**
 * The Equip/Gather GUI's real screen (design doc Section 27) -- visual style directly copied from
 * {@code socket.client.SocketingScreen} (beveled flat panel, no texture file), plus a rename
 * {@link EditBox}/button baked directly into the screen itself (not a menu slot) -- "The Allay GUI
 * will allow you to rename," same EditBox-plus-button-in-a-plain-screen idiom used everywhere else
 * in this suite for a one-off text field.
 */
public final class AllayBottleScreen extends AbstractContainerScreen<AllayBottleMenu> {

    private static final int PANEL_BODY_COLOR = 0xFFC6C6C6;
    private static final int PANEL_OUTER_BORDER_COLOR = 0xFF373737;
    private static final int PANEL_HIGHLIGHT_COLOR = 0xFFFFFFFF;
    private static final int PANEL_SHADOW_COLOR = 0xFF8B8B8B;

    private EditBox nameBox;

    public AllayBottleScreen(AllayBottleMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, AllayBottleMenu.PLAYER_INV_Y + 76 + 6);
        this.titleLabelX = 10;
        this.titleLabelY = 8;
        this.inventoryLabelY = AllayBottleMenu.PLAYER_INV_Y - 10;
    }

    @Override
    protected void init() {
        super.init();
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;

        nameBox = addRenderableWidget(new EditBox(font, x + 10, y + 22, 100, 16, Component.literal("Allay name")));
        nameBox.setMaxLength(32);
        nameBox.setValue(menu.currentName());

        addRenderableWidget(Button.builder(Component.literal("Rename"), b -> rename())
                .bounds(x + 116, y + 20, 50, 20).build());
    }

    private void rename() {
        String name = nameBox.getValue();
        if (name.isBlank()) {
            return;
        }
        var connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            connection.send(new ServerboundCustomPayloadPacket(new RenameAllayBottlePayload(name)));
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);
        graphics.text(font, "Equip", AllayBottleMenu.EQUIP_SLOT_X - 2, AllayBottleMenu.EQUIP_SLOT_Y - 10, 0xFF404040, false);
        graphics.text(font, "Gather", AllayBottleMenu.GATHER_SLOT_X - 2, AllayBottleMenu.GATHER_SLOT_Y - 10, 0xFF404040, false);
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
