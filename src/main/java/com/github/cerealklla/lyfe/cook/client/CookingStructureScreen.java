package com.github.cerealklla.lyfe.cook.client;

import java.util.List;
import java.util.Map;

import com.github.cerealklla.lyfe.cook.CookingStructureBlockEntity;
import com.github.cerealklla.lyfe.cook.CookingStructureMenu;
import com.github.cerealklla.lyfe.cook.GeneratedFoodRecipe;
import com.github.cerealklla.lyfe.structure.StructureUpgradeCost;
import com.github.cerealklla.lyfe.structure.UpgradeCostEntry;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** A cooking structure's screen -- direct structural mirror of {@code craft.client.CraftingStructureScreen}, "Cook" instead of "Craft", one fewer cost-list category (food recipes have no generic components). */
public class CookingStructureScreen extends net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<CookingStructureMenu> {

    private static final int GRID_X = 10;
    private static final int GRID_Y = 26;
    private static final int ICON_SIZE = 24;
    private static final int ICON_GAP = 2;
    private static final int ICON_CELL = ICON_SIZE + ICON_GAP;
    private static final int GRID_COLUMNS = 6;
    private static final int PREFERRED_GRID_VISIBLE_ROWS = 6;
    private static final int SCROLLBAR_WIDTH = 4;
    private static final int SCROLLBAR_GAP = 4;

    private static final int COST_X = 200;
    private static final int COST_Y = 26;
    private static final int COST_WIDTH = 110;
    private static final int COST_TITLE_HEIGHT = 14;
    private static final int COST_HEADER_HEIGHT = 12;
    private static final int COST_LINE_HEIGHT = 10;
    private static final int PREFERRED_COST_VISIBLE_LINES = 12;

    private static final int CONTENT_BOTTOM_GAP = 10;

    private static final int BUTTON_WIDTH = 90;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_MARGIN = 10;
    private static final int BUTTON_GAP = 4;

    private static final int PANEL_BODY_COLOR = 0xFFC6C6C6;
    private static final int PANEL_OUTER_BORDER_COLOR = 0xFF373737;
    private static final int PANEL_HIGHLIGHT_COLOR = 0xFFFFFFFF;
    private static final int PANEL_SHADOW_COLOR = 0xFF8B8B8B;
    private static final int LABEL_TEXT_COLOR = 0xFF404040;
    private static final int SELECTION_COLOR = 0xFFFFFFFF;

    private int selectedIndex = -1;
    private int scrollRow;
    private int costScrollOffset;
    private Button cookButton;

    private int gridVisibleRows = PREFERRED_GRID_VISIBLE_ROWS;
    private int costVisibleLines = PREFERRED_COST_VISIBLE_LINES;

    public CookingStructureScreen(CookingStructureMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 320, CookingStructureMenu.imageHeightForInventoryY(menu.inventoryY()));
        this.titleLabelX = 10;
        this.titleLabelY = 8;
    }

    @Override
    protected void init() {
        int inventoryY = menu.inventoryY();
        this.inventoryLabelY = inventoryY - 10;

        int availableGridHeight = inventoryY - CONTENT_BOTTOM_GAP - GRID_Y;
        gridVisibleRows = Math.max(1, Math.min(PREFERRED_GRID_VISIBLE_ROWS, availableGridHeight / ICON_CELL));

        int availableCostHeight = inventoryY - CONTENT_BOTTOM_GAP - costListTop();
        costVisibleLines = Math.max(1, Math.min(PREFERRED_COST_VISIBLE_LINES, availableCostHeight / COST_LINE_HEIGHT));

        super.init();
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;
        int buttonX = x + imageWidth - BUTTON_WIDTH - BUTTON_MARGIN;
        int cookY = y + imageHeight - BUTTON_HEIGHT - BUTTON_MARGIN;

        cookButton = addRenderableWidget(Button.builder(Component.literal("Cook"),
                        button -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, CookingStructureMenu.cookButtonId(selectedIndex)))
                .bounds(buttonX, cookY, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
        cookButton.active = selectedIndex >= 0;

        // Three funding-option buttons (2026-10-05) -- see craft.CraftingStructureScreen's own
        // mirror of this change for the full rationale. Gated on menu.canUpgrade() as of 2026-10-09,
        // same real spec as that screen's own mirror of this gate.
        if (menu.tier() < CookingStructureBlockEntity.MAX_STRUCTURE_TIER && menu.canUpgrade()) {
            int goldY = cookY - BUTTON_HEIGHT - BUTTON_GAP;
            int mixY = goldY - BUTTON_HEIGHT - BUTTON_GAP;
            int onHandY = mixY - BUTTON_HEIGHT - BUTTON_GAP;
            // Hover tooltip showing the actual escalating cost -- see craft.CraftingStructureScreen's
            // own mirror of this fix (2026-10-09, real report: "I don't see the cost anywhere").
            Tooltip costTooltip = Tooltip.create(upgradeCostTooltip(menu.tier() + 1));
            addRenderableWidget(Button.builder(Component.literal("Upgrade (Plot Resources)"),
                            button -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, CookingStructureMenu.UPGRADE_BUTTON_ID))
                    .bounds(buttonX, onHandY, BUTTON_WIDTH, BUTTON_HEIGHT)
                    .tooltip(costTooltip)
                    .build());
            addRenderableWidget(Button.builder(Component.literal("Upgrade (Mix + Gold)"),
                            button -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, CookingStructureMenu.UPGRADE_MIX_BUTTON_ID))
                    .bounds(buttonX, mixY, BUTTON_WIDTH, BUTTON_HEIGHT)
                    .tooltip(costTooltip)
                    .build());
            addRenderableWidget(Button.builder(Component.literal("Upgrade (Gold Only)"),
                            button -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, CookingStructureMenu.UPGRADE_GOLD_BUTTON_ID))
                    .bounds(buttonX, goldY, BUTTON_WIDTH, BUTTON_HEIGHT)
                    .tooltip(costTooltip)
                    .build());
        }
    }

    /** "Upgrade to Tier N costs: AxB, CxD" -- pure/client-computable, same {@link StructureUpgradeCost} table the server itself charges against. */
    private static Component upgradeCostTooltip(int nextTier) {
        StringBuilder sb = new StringBuilder("Upgrade to Tier ").append(nextTier).append(" costs:");
        for (UpgradeCostEntry entry : StructureUpgradeCost.costFor(nextTier)) {
            sb.append('\n').append(entry.amount()).append("x ").append(entry.label());
        }
        return Component.literal(sb.toString());
    }

    private void select(int index) {
        this.selectedIndex = index;
        this.costScrollOffset = 0;
        if (cookButton != null) {
            cookButton.active = true;
        }
    }

    private static String displayName(Identifier itemId) {
        var item = BuiltInRegistries.ITEM.getValue(itemId);
        return new ItemStack(item).getHoverName().getString();
    }

    private static String displayName(Identifier itemId, int tier) {
        return displayName(itemId) + " (T" + tier + ")";
    }

    private int maxScrollRow() {
        int rows = (menu.craftable().size() + GRID_COLUMNS - 1) / GRID_COLUMNS;
        return Math.max(0, rows - gridVisibleRows);
    }

    private int maxCostScrollOffset() {
        List<GeneratedFoodRecipe> craftable = menu.craftable();
        if (selectedIndex < 0 || selectedIndex >= craftable.size()) {
            return 0;
        }
        GeneratedFoodRecipe recipe = craftable.get(selectedIndex);
        return Math.max(0, recipe.components().size() - costVisibleLines);
    }

    private boolean isInsideGrid(int x, int y, double mouseX, double mouseY) {
        int left = x + GRID_X;
        int top = y + GRID_Y;
        return mouseX >= left && mouseX < left + GRID_COLUMNS * ICON_CELL
                && mouseY >= top && mouseY < top + gridVisibleRows * ICON_CELL;
    }

    private boolean isInsideCostList(int x, int y, double mouseX, double mouseY) {
        int listTop = y + costListTop();
        int listBottom = listTop + costVisibleLines * COST_LINE_HEIGHT;
        int left = x + COST_X;
        return mouseX >= left && mouseX < left + COST_WIDTH && mouseY >= listTop && mouseY < listBottom;
    }

    private static int costListTop() {
        return COST_Y + COST_TITLE_HEIGHT + COST_HEADER_HEIGHT;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;
        if (isInsideGrid(x, y, event.x(), event.y())) {
            int col = (int) ((event.x() - (x + GRID_X)) / ICON_CELL);
            int row = (int) ((event.y() - (y + GRID_Y)) / ICON_CELL);
            int index = (scrollRow + row) * GRID_COLUMNS + col;
            List<GeneratedFoodRecipe> craftable = menu.craftable();
            if (index >= 0 && index < craftable.size()) {
                select(index);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;
        if (isInsideGrid(x, y, mouseX, mouseY)) {
            scrollRow = Math.max(0, Math.min(maxScrollRow(), scrollRow - (int) Math.signum(scrollY)));
            return true;
        }
        if (isInsideCostList(x, y, mouseX, mouseY)) {
            costScrollOffset = Math.max(0, Math.min(maxCostScrollOffset(), costScrollOffset - (int) Math.signum(scrollY)));
            return true;
        }
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;
        List<GeneratedFoodRecipe> craftable = menu.craftable();

        for (int row = 0; row < gridVisibleRows; row++) {
            for (int col = 0; col < GRID_COLUMNS; col++) {
                int index = (scrollRow + row) * GRID_COLUMNS + col;
                if (index >= craftable.size()) {
                    continue;
                }
                int cellX = x + GRID_X + col * ICON_CELL;
                int cellY = y + GRID_Y + row * ICON_CELL;
                if (index == selectedIndex) {
                    graphics.fill(cellX - 1, cellY - 1, cellX + ICON_SIZE + 1, cellY + ICON_SIZE + 1, SELECTION_COLOR);
                }
                var item = BuiltInRegistries.ITEM.getValue(craftable.get(index).resultId());
                graphics.item(new ItemStack(item), cellX + 4, cellY + 4);
                if (mouseX >= cellX && mouseX < cellX + ICON_SIZE && mouseY >= cellY && mouseY < cellY + ICON_SIZE) {
                    graphics.setTooltipForNextFrame(font, Component.literal(displayName(craftable.get(index).resultId(), craftable.get(index).tier())), mouseX, mouseY);
                }
            }
        }

        if (maxScrollRow() > 0) {
            int trackX = x + GRID_X + GRID_COLUMNS * ICON_CELL + SCROLLBAR_GAP;
            int trackY = y + GRID_Y;
            int trackHeight = gridVisibleRows * ICON_CELL;
            drawScrollbar(graphics, trackX, trackY, trackHeight, scrollRow, maxScrollRow());
        }

        if (selectedIndex >= 0 && selectedIndex < craftable.size()) {
            GeneratedFoodRecipe recipe = craftable.get(selectedIndex);
            int lineY = y + COST_Y;
            graphics.text(font, Component.literal(displayName(recipe.resultId(), recipe.tier())), x + COST_X, lineY, LABEL_TEXT_COLOR, false);
            lineY += COST_TITLE_HEIGHT;
            graphics.text(font, Component.literal("Cost:"), x + COST_X, lineY, LABEL_TEXT_COLOR, false);
            lineY += COST_HEADER_HEIGHT;

            int listTop = lineY;
            int listBottom = listTop + costVisibleLines * COST_LINE_HEIGHT;
            graphics.enableScissor(x + COST_X, listTop, x + COST_X + COST_WIDTH, listBottom);
            int drawY = listTop - costScrollOffset * COST_LINE_HEIGHT;
            for (Map.Entry<Identifier, Integer> entry : recipe.components().entrySet()) {
                if (drawY + COST_LINE_HEIGHT >= listTop && drawY <= listBottom) {
                    String line = entry.getValue() + "x " + displayName(entry.getKey());
                    graphics.text(font, Component.literal(line), x + COST_X, drawY, LABEL_TEXT_COLOR, false);
                }
                drawY += COST_LINE_HEIGHT;
            }
            graphics.disableScissor();

            if (maxCostScrollOffset() > 0) {
                int trackX = x + COST_X + COST_WIDTH + SCROLLBAR_GAP;
                drawScrollbar(graphics, trackX, listTop, listBottom - listTop, costScrollOffset, maxCostScrollOffset());
            }
        }
    }

    private void drawScrollbar(GuiGraphicsExtractor graphics, int trackX, int trackY, int trackHeight, int position, int maxPosition) {
        graphics.fill(trackX, trackY, trackX + SCROLLBAR_WIDTH, trackY + trackHeight, PANEL_SHADOW_COLOR);
        int thumbHeight = Math.max(8, trackHeight / (maxPosition + 2));
        int thumbY = trackY + (trackHeight - thumbHeight) * position / Math.max(1, maxPosition);
        graphics.fill(trackX, thumbY, trackX + SCROLLBAR_WIDTH, thumbY + thumbHeight, PANEL_OUTER_BORDER_COLOR);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);
        if (menu.craftable().isEmpty()) {
            graphics.text(font, Component.literal("You don't know any recipes yet."), GRID_X, GRID_Y, LABEL_TEXT_COLOR, false);
            graphics.text(font, Component.literal("Research food at a Research Bench."), GRID_X, GRID_Y + 12, LABEL_TEXT_COLOR, false);
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
