package com.github.cerealklla.lyfe.menu.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.github.cerealklla.lyfe.magician.PlayerSpellProficiency;
import com.github.cerealklla.lyfe.magician.SpellIds;
import com.github.cerealklla.lyfe.magician.SpellProficiencyCurve;
import com.github.cerealklla.lyfe.registration.ModAttachments;
import com.github.cerealklla.nuclyus.menu.client.NuclyusMenu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * "Spells" tab -- explicit request, 2026-10-20, picked up right alongside the Nature Staff tooltip
 * fix for the same reason (both are "tell the player what Spark of Life actually does"). Shows
 * every known spell's current Proficiency level and live cost/cooldown, same shape as {@code
 * HunterJournalTab}'s own level/tooltip-per-row layout. Only one spell exists right now (Spark of
 * Life), but this is written generically over {@code SpellIds}' own id list rather than hardcoded
 * to that one entry, so a future second spell needs no changes here at all.
 */
public final class SpellsTab implements NuclyusMenu {

    private static final int MARGIN = 10;
    private static final int ROW_HEIGHT = 12;
    private static final int NAME_COLOR = 0xFFE0E0E0;
    private static final int STAT_COLOR = 0xFF9090FF;

    private record Row(String name, int level, int maxLevel, Component tooltip) {
    }

    private List<Row> rows = List.of();
    private int scrollOffset;

    @Override
    public Component title() {
        return Component.literal("Spells");
    }

    @Override
    public int order() {
        return 8;
    }

    @Override
    public void onShown() {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            rows = List.of();
            return;
        }
        PlayerSpellProficiency data = player.getData(ModAttachments.PLAYER_SPELL_PROFICIENCY);
        List<Row> built = new ArrayList<>();
        for (Identifier spellId : SpellIds.ALL) {
            int progress = data.progress(spellId);
            int level = SpellProficiencyCurve.levelForProgress(progress);
            int manaCost = SpellProficiencyCurve.manaCost(level);
            double cooldownSeconds = SpellProficiencyCurve.cooldownTicks(level) / 20.0;
            Component tooltip = Component.literal(String.format(Locale.ROOT,
                    "%s -- %d mana, %.1fs cooldown. Cast by right-clicking a Nature Staff.",
                    displayName(spellId), manaCost, cooldownSeconds));
            built.add(new Row(displayName(spellId), level, SpellProficiencyCurve.MAX_LEVEL, tooltip));
        }
        rows = built;
        scrollOffset = 0;
    }

    private static String displayName(Identifier spellId) {
        return switch (spellId.getPath()) {
            case "spark_of_life" -> "Spark of Life";
            default -> spellId.getPath();
        };
    }

    @Override
    public void onHidden() {
    }

    @Override
    public boolean mouseScrolled(Rect2i area, double mouseX, double mouseY, double scrollX, double scrollY) {
        int viewport = area.getHeight() - 2 * MARGIN;
        int contentHeight = rows.size() * ROW_HEIGHT;
        int maxScroll = Math.max(0, contentHeight - viewport);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) (scrollY * ROW_HEIGHT * 2)));
        return true;
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, Rect2i area, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        int left = area.getX() + MARGIN;
        int top = area.getY() + MARGIN;
        int bottom = area.getY() + area.getHeight() - MARGIN;
        int right = area.getX() + area.getWidth() - MARGIN;

        if (rows.isEmpty()) {
            graphics.text(font, Component.literal("No spells known yet."), left, top, NAME_COLOR, false);
            return;
        }

        graphics.enableScissor(left, top, right, bottom);
        int y = top - scrollOffset;
        Component hoveredTooltip = null;
        for (Row row : rows) {
            if (y + ROW_HEIGHT >= top && y <= bottom) {
                graphics.text(font, Component.literal(row.name()), left, y, NAME_COLOR, false);
                String levelText = "Level " + row.level() + "/" + row.maxLevel();
                int levelX = right - font.width(levelText);
                graphics.text(font, Component.literal(levelText), levelX, y, STAT_COLOR, false);
                if (mouseX >= left && mouseX < right && mouseY >= y && mouseY < y + ROW_HEIGHT) {
                    hoveredTooltip = row.tooltip();
                }
            }
            y += ROW_HEIGHT;
        }
        graphics.disableScissor();

        if (hoveredTooltip != null) {
            graphics.setTooltipForNextFrame(font, hoveredTooltip, mouseX, mouseY);
        }
    }
}
