package com.github.cerealklla.lyfe.menu.client;

import java.util.ArrayList;
import java.util.List;

import com.github.cerealklla.lyfe.api.Lyfe;
import com.github.cerealklla.lyfe.skill.SkillBenefits;
import com.github.cerealklla.lyfe.skill.SkillCopy;
import com.github.cerealklla.lyfe.skill.SkillDefinition;
import com.github.cerealklla.nuclyus.menu.client.NuclyusMenu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * "Skills" tab -- direct port of the former standalone {@code skill.client.SkillsScreen} into the
 * shared menu, 2026-10-17. Behavior unchanged -- see that class's own removed doc for the full
 * design rationale (read-only, no server round trip, every value a pure function of the already-
 * synced {@code PLAYER_SKILLS} level via {@link SkillBenefits}). Scroll/row-drawing logic is
 * otherwise identical, just against the injected {@code area} instead of this class's own
 * (nonexistent) {@code width}/{@code height} fields.
 */
public final class SkillsTab implements NuclyusMenu {

    private static final int CONTENT_MARGIN = 10;
    private static final int ICON_SIZE = 16;
    private static final int LINE_HEIGHT = 10;
    private static final int ROW_GAP = 8;

    private static final int LABEL_TEXT_COLOR = 0xFFE0E0E0;
    private static final int BENEFIT_TEXT_COLOR = 0xFF7FD17F;

    private static final int HEADER_HEIGHT = ICON_SIZE + 2;
    private static final int BENEFIT_INDENT = 6;

    private record Row(ItemStack icon, String name, List<String> xpLines, List<String> summaryLines, int level,
                        int maxLevel, List<List<String>> benefitLines, int height) {
    }

    private final List<Row> rows = new ArrayList<>();
    private int contentHeight;
    private int scrollOffset;

    @Override
    public Component title() {
        return Component.literal("Skills");
    }

    @Override
    public int order() {
        return 4;
    }

    @Override
    public void onShown() {
        rows.clear();
        var player = Minecraft.getInstance().player;
        Font font = Minecraft.getInstance().font;
        int wrapWidth = 400 - 2 * CONTENT_MARGIN; // generous fixed wrap width; actual area is usually wider
        int y = 0;
        for (SkillDefinition def : Lyfe.getAvailableSkills()) {
            SkillCopy copy = SkillCopy.get(def.id());
            int level = player != null ? Lyfe.getLevel(player, def.id()) : 0;
            int maxLevel = def.xpCurve().maxLevel();
            List<String> benefits = SkillBenefits.describe(def.id(), player, level, maxLevel);
            List<String> xpLines = wrapText(font, "XP: " + copy.xpSource(), wrapWidth);
            List<String> summaryLines = wrapText(font, copy.summary(), wrapWidth);
            List<List<String>> benefitLines = benefits.stream()
                    .map(benefit -> wrapText(font, benefit, wrapWidth - BENEFIT_INDENT))
                    .toList();
            int benefitLineCount = benefitLines.stream().mapToInt(List::size).sum();
            int height = HEADER_HEIGHT + LINE_HEIGHT * (xpLines.size() + summaryLines.size() + 1 + benefitLineCount) + ROW_GAP;
            rows.add(new Row(new ItemStack(copy.icon()), def.displayName(), xpLines, summaryLines,
                    level, maxLevel, benefitLines, height));
            y += height;
        }
        contentHeight = y;
        scrollOffset = 0;
    }

    @Override
    public void onHidden() {
    }

    private static List<String> wrapText(Font font, String text, int maxWidth) {
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

    private int maxScrollOffset(Rect2i area) {
        int viewport = area.getHeight() - 2 * CONTENT_MARGIN;
        return Math.max(0, contentHeight - viewport);
    }

    @Override
    public boolean mouseScrolled(Rect2i area, double mouseX, double mouseY, double scrollX, double scrollY) {
        scrollOffset = Math.max(0, Math.min(maxScrollOffset(area), scrollOffset - (int) (scrollY * LINE_HEIGHT * 2)));
        return true;
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, Rect2i area, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        int top = area.getY() + CONTENT_MARGIN;
        int bottom = area.getY() + area.getHeight() - CONTENT_MARGIN;
        int left = area.getX() + CONTENT_MARGIN;
        int right = area.getX() + area.getWidth() - CONTENT_MARGIN;

        graphics.enableScissor(left, top, right, bottom);
        int rowY = top - scrollOffset;
        for (Row row : rows) {
            if (rowY + row.height() >= top && rowY <= bottom) {
                drawRow(graphics, font, row, left, rowY, right - left);
            }
            rowY += row.height();
        }
        graphics.disableScissor();
    }

    private void drawRow(GuiGraphicsExtractor graphics, Font font, Row row, int x, int y, int width) {
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
}
