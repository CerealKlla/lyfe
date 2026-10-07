package com.github.cerealklla.lyfe.skill.client;

import java.util.ArrayList;
import java.util.List;

import com.github.cerealklla.lyfe.api.Lyfe;
import com.github.cerealklla.lyfe.skill.SkillBenefits;
import com.github.cerealklla.lyfe.skill.SkillCopy;
import com.github.cerealklla.lyfe.skill.SkillDefinition;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * The Skills screen (design doc Section 13) -- a scrollable list of every available skill showing
 * an icon, what grants its XP, a one-line summary, the player's current level, and live-computed
 * benefit lines. Built as a fully custom screen per the user's explicit 2026-10-02 decision not to
 * reuse vanilla's Advancement screen (vanilla advancements are boolean/criteria-based, not built for
 * a live numeric readout that changes with level). Read-only -- no server round-trip needed, since
 * {@code ModAttachments.PLAYER_SKILLS} is already synced to the client and every benefit formula is
 * a pure function of level (see {@link SkillBenefits}).
 *
 * <p>Styling matches the house vanilla-chrome convention established this session in
 * {@code craft.client.CraftingStructureScreen}/{@code research.client.ResearchScreen}: same panel
 * bevel colors, same {@code GuiGraphicsExtractor} overrides, colors always carrying their {@code
 * 0xFF} alpha prefix (the exact bug hit twice already in those two screens). Scrolling reuses the
 * scissor-clip pattern from {@code minimap.MinimapOverlay} ({@code enableScissor}/{@code
 * disableScissor} is a real stack -- always paired 1:1, never left unbalanced) rather than
 * {@code Kyt.loadout.client.ItemPaletteScreen}'s row-based scroll, since rows here have variable
 * height (Historian's is short, Crafter's is long) -- a pixel offset fits that better than a row
 * count.
 */
public class SkillsScreen extends Screen {

    private static final int PANEL_WIDTH = 300;
    private static final int PANEL_HEIGHT = 230;
    private static final int CONTENT_MARGIN = 10;
    private static final int ICON_SIZE = 16;
    private static final int LINE_HEIGHT = 10;
    private static final int ROW_GAP = 8;

    private static final int PANEL_BODY_COLOR = 0xFFC6C6C6;
    private static final int PANEL_OUTER_BORDER_COLOR = 0xFF373737;
    private static final int PANEL_HIGHLIGHT_COLOR = 0xFFFFFFFF;
    private static final int PANEL_SHADOW_COLOR = 0xFF8B8B8B;
    private static final int LABEL_TEXT_COLOR = 0xFF404040;
    private static final int BENEFIT_TEXT_COLOR = 0xFF2D6B2D;

    private static final int HEADER_HEIGHT = ICON_SIZE + 2;

    private static final int BENEFIT_INDENT = 6;

    private record Row(ItemStack icon, String name, List<String> xpLines, List<String> summaryLines, int level,
                        int maxLevel, List<List<String>> benefitLines, int height) {
    }

    private final List<Row> rows = new ArrayList<>();
    private int contentHeight;
    private int scrollOffset;

    public SkillsScreen() {
        super(Component.literal("Skills"));
    }

    @Override
    protected void init() {
        rows.clear();
        var player = Minecraft.getInstance().player;
        int wrapWidth = PANEL_WIDTH - 2 * CONTENT_MARGIN;
        int y = 0;
        for (SkillDefinition def : Lyfe.getAvailableSkills()) {
            SkillCopy copy = SkillCopy.get(def.id());
            int level = player != null ? Lyfe.getLevel(player, def.id()) : 0;
            int maxLevel = def.xpCurve().maxLevel();
            List<String> benefits = SkillBenefits.describe(def.id(), player, level, maxLevel);
            List<String> xpLines = wrapText("XP: " + copy.xpSource(), wrapWidth);
            List<String> summaryLines = wrapText(copy.summary(), wrapWidth);
            List<List<String>> benefitLines = benefits.stream()
                    .map(benefit -> wrapText(benefit, wrapWidth - BENEFIT_INDENT))
                    .toList();
            int benefitLineCount = benefitLines.stream().mapToInt(List::size).sum();
            // header (icon+name) + one line per level readout + every wrapped xp/summary/benefit line + gap after.
            int height = HEADER_HEIGHT + LINE_HEIGHT * (xpLines.size() + summaryLines.size() + 1 + benefitLineCount) + ROW_GAP;
            rows.add(new Row(new ItemStack(copy.icon()), def.displayName(), xpLines, summaryLines,
                    level, maxLevel, benefitLines, height));
            y += height;
        }
        contentHeight = y;
        scrollOffset = 0;
    }

    /** Greedy word-wrap against the panel's real content width -- {@code GuiGraphicsExtractor} has no built-in wrapping helper used elsewhere in this mod. */
    private List<String> wrapText(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (font.width(candidate) > maxWidth && !current.isEmpty()) {
                lines.add(current.toString());
                current = new StringBuilder(word);
            } else {
                current = new StringBuilder(candidate);
            }
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        return lines.isEmpty() ? List.of("") : lines;
    }

    private int contentTop(int panelY) {
        return panelY + CONTENT_MARGIN + LINE_HEIGHT + 4; // below the title
    }

    private int contentBottom(int panelY) {
        return panelY + PANEL_HEIGHT - CONTENT_MARGIN;
    }

    private int maxScrollOffset(int panelY) {
        int viewport = contentBottom(panelY) - contentTop(panelY);
        return Math.max(0, contentHeight - viewport);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        int panelY = (height - PANEL_HEIGHT) / 2;
        scrollOffset = Math.max(0, Math.min(maxScrollOffset(panelY), scrollOffset - (int) (scrollY * LINE_HEIGHT * 2)));
        return true;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int xo = (width - PANEL_WIDTH) / 2;
        int yo = (height - PANEL_HEIGHT) / 2;

        graphics.fill(xo, yo, xo + PANEL_WIDTH, yo + PANEL_HEIGHT, PANEL_OUTER_BORDER_COLOR);
        graphics.fill(xo + 1, yo + 1, xo + PANEL_WIDTH - 1, yo + PANEL_HEIGHT - 1, PANEL_HIGHLIGHT_COLOR);
        graphics.fill(xo + 2, yo + 2, xo + PANEL_WIDTH - 1, yo + PANEL_HEIGHT - 1, PANEL_SHADOW_COLOR);
        graphics.fill(xo + 2, yo + 2, xo + PANEL_WIDTH - 2, yo + PANEL_HEIGHT - 2, PANEL_BODY_COLOR);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int xo = (width - PANEL_WIDTH) / 2;
        int yo = (height - PANEL_HEIGHT) / 2;

        graphics.text(font, title, xo + CONTENT_MARGIN, yo + CONTENT_MARGIN, LABEL_TEXT_COLOR, false);

        int top = contentTop(yo);
        int bottom = contentBottom(yo);
        int left = xo + CONTENT_MARGIN;
        int right = xo + PANEL_WIDTH - CONTENT_MARGIN;

        graphics.enableScissor(left, top, right, bottom);
        int rowY = top - scrollOffset;
        for (Row row : rows) {
            if (rowY + row.height() >= top && rowY <= bottom) {
                drawRow(graphics, row, left, rowY, right - left);
            }
            rowY += row.height();
        }
        graphics.disableScissor();
    }

    private void drawRow(GuiGraphicsExtractor graphics, Row row, int x, int y, int width) {
        graphics.item(row.icon(), x, y);
        graphics.text(font, Component.literal(row.name()), x + ICON_SIZE + 4, y + 3, LABEL_TEXT_COLOR, false);

        int lineY = y + HEADER_HEIGHT;
        for (String line : row.xpLines()) {
            graphics.text(font, Component.literal(line), x, lineY, LABEL_TEXT_COLOR, false);
            lineY += LINE_HEIGHT;
        }
        for (String line : row.summaryLines()) {
            graphics.text(font, Component.literal(line), x, lineY, LABEL_TEXT_COLOR, false);
            lineY += LINE_HEIGHT;
        }
        graphics.text(font, Component.literal("Current Level: " + row.level() + " / " + row.maxLevel()), x, lineY, LABEL_TEXT_COLOR, false);
        lineY += LINE_HEIGHT;
        for (List<String> benefit : row.benefitLines()) {
            for (int i = 0; i < benefit.size(); i++) {
                String prefix = i == 0 ? "- " : "  ";
                graphics.text(font, Component.literal(prefix + benefit.get(i)), x + BENEFIT_INDENT, lineY, BENEFIT_TEXT_COLOR, false);
                lineY += LINE_HEIGHT;
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
