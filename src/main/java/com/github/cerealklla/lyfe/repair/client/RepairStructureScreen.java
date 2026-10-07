package com.github.cerealklla.lyfe.repair.client;

import com.github.cerealklla.lyfe.repair.RepairCost;
import com.github.cerealklla.lyfe.repair.RepairStructureMenu;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * The new Anvil/Grindstone repair screen (2026-10-07 user request) -- one input slot and the same 3
 * funding-option buttons {@code craft.client.CraftingStructureScreen} already has (On-Hand/Mix/
 * Gold-Only), stacked above the player-inventory panel instead of that screen's bottom-right corner
 * since there's no recipe list competing for space here. Visual style (beveled light-gray panel,
 * drawn slot backdrops) directly copied from {@code research.client.ResearchScreen}.
 *
 * <p>A live "Needs: Nx Material" line (2026-10-07 follow-up request) is drawn just below the input
 * slot via {@link #extractLabels} -- pure client-side math off {@link RepairCost#describeFor}, which
 * only reads the already-synced {@link ItemStack}'s own damage/max-damage, no networking needed; it
 * just re-reads {@code menu.repairInput()} every frame so it stays live as the player swaps items.
 */
public class RepairStructureScreen extends AbstractContainerScreen<RepairStructureMenu> {

    private static final int BUTTON_WIDTH = 150;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 4;
    private static final int COST_LABEL_Y = 40;
    private static final int BUTTONS_TOP_Y = 54;

    private static final int PANEL_BODY_COLOR = 0xFFC6C6C6;
    private static final int PANEL_OUTER_BORDER_COLOR = 0xFF373737;
    private static final int PANEL_HIGHLIGHT_COLOR = 0xFFFFFFFF;
    private static final int PANEL_SHADOW_COLOR = 0xFF8B8B8B;

    public RepairStructureScreen(RepairStructureMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, RepairStructureMenu.PLAYER_INV_Y + 76 + 6);
        this.titleLabelX = 10;
        this.titleLabelY = 8;
        this.inventoryLabelY = RepairStructureMenu.PLAYER_INV_Y - 10;
    }

    @Override
    protected void init() {
        super.init();
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;
        int buttonX = x + (imageWidth - BUTTON_WIDTH) / 2;

        addRenderableWidget(Button.builder(Component.literal("Repair with Resources"),
                        button -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, RepairStructureMenu.REPAIR_RESOURCES_BUTTON_ID))
                .bounds(buttonX, y + BUTTONS_TOP_Y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Repair with Resources and Gold"),
                        button -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, RepairStructureMenu.REPAIR_MIX_BUTTON_ID))
                .bounds(buttonX, y + BUTTONS_TOP_Y + BUTTON_HEIGHT + BUTTON_GAP, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Repair with Gold"),
                        button -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, RepairStructureMenu.REPAIR_GOLD_BUTTON_ID))
                .bounds(buttonX, y + BUTTONS_TOP_Y + 2 * (BUTTON_HEIGHT + BUTTON_GAP), BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);
        Component needs = RepairCost.describeFor(menu.repairInput());
        if (needs != null) {
            int textWidth = font.width(needs);
            graphics.text(font, needs, (imageWidth - textWidth) / 2, COST_LABEL_Y, 0xFF404040, false);
        }
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
