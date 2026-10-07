package com.github.cerealklla.lyfe.craft.client;

import java.util.List;
import java.util.Map;

import com.github.cerealklla.lyfe.craft.CraftingStructureBlockEntity;
import com.github.cerealklla.lyfe.craft.CraftingStructureMenu;
import com.github.cerealklla.lyfe.craft.GeneratedRecipe;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * A crafting structure's screen -- **reworked again 2026-10-03** after a Tier 5 playtest (25+ known
 * recipes) surfaced two real overflow bugs: a fixed 6-column icon grid ran down into the Inventory
 * row once recipes exceeded 24, and a long ingredient list (a real Tier-5-reachable Diamond Hoe
 * recipe had 15 lines) ran straight through the Craft/Upgrade buttons, which sat at fixed mid-panel
 * Y coordinates. Both the recipe grid and the ingredient cost list are now independently scrollable
 * (mouse wheel, routed by cursor position), and Craft/Upgrade moved to the bottom-right corner so
 * they can never collide with either list regardless of length. "Upgrade Station" is no longer
 * created at all once a structure is already Tier 5 (previously it existed but just replied "already
 * the highest tier").
 *
 * <p>The recipe grid is no longer one real {@code Button} per recipe (today's single biggest reason
 * nothing could scroll -- a persistent widget can't be made to stop intercepting clicks once
 * scrolled off without manually toggling every one of them every scroll tick). It now mirrors
 * {@code Kyt.loadout.client.ItemPaletteScreen}'s row-based model instead: a fixed-size visible grid,
 * a {@code scrollRow} offset, and a {@code mouseClicked} hit-test that maps a click back to a
 * {@code craftable} index -- clipping is implicit (only the visible-row window is ever drawn/hit-
 * tested) rather than needing a scissor region. The ingredient cost list, by contrast, reuses
 * {@code skill.client.SkillsScreen}'s real {@code enableScissor}/{@code disableScissor} pattern,
 * since it's a plain text column rather than a uniform grid.
 */
public class CraftingStructureScreen extends net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<CraftingStructureMenu> {

    private static final int GRID_X = 10;
    private static final int GRID_Y = 26;
    private static final int ICON_SIZE = 24;
    private static final int ICON_GAP = 2;
    private static final int ICON_CELL = ICON_SIZE + ICON_GAP;
    private static final int GRID_COLUMNS = 6;
    // Both visible-window sizes extended 2026-10-03 (user request: "why did you make this scroll
    // area so small? extend it") -- these are now treated as the PREFERRED/maximum counts only; the
    // real per-client counts are computed in init() from the player's actual window, since a fixed
    // 300-tall panel turned out to overflow some Auto-GUI-Scale resolutions (vanilla only guarantees
    // a logical canvas of 320x240, not 320x300 -- found live, see the shrink-to-fit logic below).
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

    // Vanilla's own classic GUI chrome colors (confirmed matches this version's default title color,
    // -12566464 == 0xFF404040) -- restyled 2026-10-02 per user request to look like a standard
    // Minecraft UI panel instead of a flat dark custom theme.
    private static final int PANEL_BODY_COLOR = 0xFFC6C6C6;
    private static final int PANEL_OUTER_BORDER_COLOR = 0xFF373737;
    private static final int PANEL_HIGHLIGHT_COLOR = 0xFFFFFFFF;
    private static final int PANEL_SHADOW_COLOR = 0xFF8B8B8B;
    private static final int LABEL_TEXT_COLOR = 0xFF404040;
    private static final int SELECTION_COLOR = 0xFFFFFFFF;

    private int selectedIndex = -1;
    private int scrollRow;
    private int costScrollOffset;
    private Button craftButton;

    // The real per-client visible-row/line counts -- computed in init() from the inventory-row Y
    // the menu was actually constructed with (see ModMenus' client factory /
    // CraftingStructureMenu#computeInventoryY -- Slot y is final in this version, so that value had
    // to be decided before construction, not shifted afterward).
    private int gridVisibleRows = PREFERRED_GRID_VISIBLE_ROWS;
    private int costVisibleLines = PREFERRED_COST_VISIBLE_LINES;

    // Shrink-to-fit (2026-10-03 fix): vanilla's Auto GUI Scale only guarantees a logical canvas of
    // at least 320x240, not 320x300 -- found live when a full-screen player's chosen scale left them
    // with a logical height in that gap, overflowing this panel's fixed 300-tall design off the top
    // of the screen entirely. `imageHeight` is `final` on AbstractContainerScreen (only settable via
    // the super constructor), so it's derived here from the menu's own `inventoryY()` -- which
    // ModMenus' client factory already computed from the real window size before this screen even
    // existed -- rather than from a separately-recomputed value that could drift out of sync.
    public CraftingStructureScreen(CraftingStructureMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 320, CraftingStructureMenu.imageHeightForInventoryY(menu.inventoryY()));
        this.titleLabelX = 10;
        this.titleLabelY = 8;
    }

    @Override
    protected void init() {
        // Grid/cost scroll-window sizes derive from whatever room the menu's fixed inventoryY left
        // above it, so a roomy window still gets the full 6-row/12-line experience while a cramped
        // one degrades gracefully instead of overflowing.
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
        int craftY = y + imageHeight - BUTTON_HEIGHT - BUTTON_MARGIN;

        craftButton = addRenderableWidget(Button.builder(Component.literal("Craft"),
                        button -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, CraftingStructureMenu.craftButtonId(selectedIndex)))
                .bounds(buttonX, craftY, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
        craftButton.active = selectedIndex >= 0;

        // Simply never created at the structure ladder's own top tier (2026-10-03, user request) --
        // there's nothing higher to upgrade to, so it no longer exists-but-always-fails the way it
        // used to. Compared against CraftingStructureBlockEntity.MAX_STRUCTURE_TIER, NOT
        // EquipmentTierLadder.MAX_TIER -- those are separate axes that only coincidentally matched
        // before Copper's insertion made the equipment ladder longer than the 5 real structure blocks.
        // Three funding-option buttons (2026-10-05, replacing the old single free/instant "Upgrade
        // Station" button) -- see structure.StructureUpgradeFunding's own doc for what each option
        // actually costs. Stacked directly above the Craft button; a Settlemynts-less server or a
        // structure not on any plot still treats all three identically as free/instant (graceful
        // degradation handled entirely server-side, see CraftingStructureMenu#upgradeStructure).
        if (menu.tier() < CraftingStructureBlockEntity.MAX_STRUCTURE_TIER) {
            int goldY = craftY - BUTTON_HEIGHT - BUTTON_GAP;
            int mixY = goldY - BUTTON_HEIGHT - BUTTON_GAP;
            int onHandY = mixY - BUTTON_HEIGHT - BUTTON_GAP;
            addRenderableWidget(Button.builder(Component.literal("Upgrade (Plot Resources)"),
                            button -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, CraftingStructureMenu.UPGRADE_BUTTON_ID))
                    .bounds(buttonX, onHandY, BUTTON_WIDTH, BUTTON_HEIGHT)
                    .build());
            addRenderableWidget(Button.builder(Component.literal("Upgrade (Mix + Gold)"),
                            button -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, CraftingStructureMenu.UPGRADE_MIX_BUTTON_ID))
                    .bounds(buttonX, mixY, BUTTON_WIDTH, BUTTON_HEIGHT)
                    .build());
            addRenderableWidget(Button.builder(Component.literal("Upgrade (Gold Only)"),
                            button -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, CraftingStructureMenu.UPGRADE_GOLD_BUTTON_ID))
                    .bounds(buttonX, goldY, BUTTON_WIDTH, BUTTON_HEIGHT)
                    .build());
        }
    }

    private void select(int index) {
        this.selectedIndex = index;
        this.costScrollOffset = 0;
        if (craftButton != null) {
            craftButton.active = true;
        }
    }

    private static String displayName(Identifier itemId) {
        var item = BuiltInRegistries.ITEM.getValue(itemId);
        return new ItemStack(item).getHoverName().getString();
    }

    private int maxScrollRow() {
        int rows = (menu.craftable().size() + GRID_COLUMNS - 1) / GRID_COLUMNS;
        return Math.max(0, rows - gridVisibleRows);
    }

    private int maxCostScrollOffset() {
        List<GeneratedRecipe> craftable = menu.craftable();
        if (selectedIndex < 0 || selectedIndex >= craftable.size()) {
            return 0;
        }
        GeneratedRecipe recipe = craftable.get(selectedIndex);
        int lineCount = recipe.specificComponents().size() + recipe.genericComponents().size();
        return Math.max(0, lineCount - costVisibleLines);
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
        // Checked BEFORE super (unlike mouseScrolled below) -- AbstractContainerScreen#mouseClicked
        // (unlike plain Screen) swallows clicks anywhere within the panel bounds for its own
        // slot-drag tracking, returning true even when no real Slot is under the cursor. Since
        // nothing in this screen's menu ever places a real Slot over the grid area, checking our own
        // hit-test first is always safe and is what actually lets a grid click register at all.
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;
        if (isInsideGrid(x, y, event.x(), event.y())) {
            int col = (int) ((event.x() - (x + GRID_X)) / ICON_CELL);
            int row = (int) ((event.y() - (y + GRID_Y)) / ICON_CELL);
            int index = (scrollRow + row) * GRID_COLUMNS + col;
            List<GeneratedRecipe> craftable = menu.craftable();
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
        List<GeneratedRecipe> craftable = menu.craftable();

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
                    graphics.setTooltipForNextFrame(font, Component.literal(displayName(craftable.get(index).resultId())), mouseX, mouseY);
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
            GeneratedRecipe recipe = craftable.get(selectedIndex);
            int lineY = y + COST_Y;
            graphics.text(font, Component.literal(displayName(recipe.resultId())), x + COST_X, lineY, LABEL_TEXT_COLOR, false);
            lineY += COST_TITLE_HEIGHT;
            graphics.text(font, Component.literal("Cost:"), x + COST_X, lineY, LABEL_TEXT_COLOR, false);
            lineY += COST_HEADER_HEIGHT;

            int listTop = lineY;
            int listBottom = listTop + costVisibleLines * COST_LINE_HEIGHT;
            graphics.enableScissor(x + COST_X, listTop, x + COST_X + COST_WIDTH, listBottom);
            int drawY = listTop - costScrollOffset * COST_LINE_HEIGHT;
            for (Map.Entry<Identifier, Integer> entry : recipe.specificComponents().entrySet()) {
                if (drawY + COST_LINE_HEIGHT >= listTop && drawY <= listBottom) {
                    String line = entry.getValue() + "x " + displayName(entry.getKey());
                    graphics.text(font, Component.literal(line), x + COST_X, drawY, LABEL_TEXT_COLOR, false);
                }
                drawY += COST_LINE_HEIGHT;
            }
            // Generic ("Any Log"-style) requirements: the group name is already the display
            // string, no displayName(Identifier) lookup needed/possible -- it isn't a real item id.
            for (Map.Entry<String, Integer> entry : recipe.genericComponents().entrySet()) {
                if (drawY + COST_LINE_HEIGHT >= listTop && drawY <= listBottom) {
                    String line = entry.getValue() + "x " + entry.getKey();
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

    /** A plain filled-rect track + thumb -- first-pass placeholder styling, consistent with this screen's own hand-drawn chrome rather than vanilla's creative-screen scrollbar sprite. */
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
            graphics.text(font, Component.literal("Research an item at a Research Bench."), GRID_X, GRID_Y + 12, LABEL_TEXT_COLOR, false);
        }
        // No placeholder when nothing is selected yet -- user feedback, 2026-10-02: it ran off the
        // edge of the panel and wasn't necessary (an empty cost panel already reads as "nothing
        // selected"). The actual cost list/title now render in extractRenderState instead of here --
        // moved 2026-10-03 so enableScissor's absolute screen coordinates line up correctly with the
        // (also-absolute) draw coordinates used there; extractLabels receives an already-translated
        // local coordinate space, which would otherwise mismatch the scissor rect.
    }

    /** One vanilla-style "sunken" slot backdrop (dark outline, mid-gray interior) at a local (x, y). */
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

        // Vanilla's classic beveled panel: a dark outline, a light top/left highlight, a darker
        // bottom/right shadow, and the flat gray body in between.
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
