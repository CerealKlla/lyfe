package com.github.cerealklla.lyfe.menu.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.github.cerealklla.lyfe.huntsman.HuntsmanListener;
import com.github.cerealklla.lyfe.huntsman.PlayerHuntsman;
import com.github.cerealklla.lyfe.huntsman.QuarryKnowledgeCurve;
import com.github.cerealklla.lyfe.registration.ModAttachments;
import com.github.cerealklla.nuclyus.menu.client.NuclyusMenu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * "Hunter's Journal" tab -- current Quarry Knowledge (Huntsman skill) for every mob type the
 * player has engaged, tooltip shows the per-level damage perk and progress to the next level.
 * Reads the now-synced {@code ModAttachments.PLAYER_HUNTSMAN} attachment directly (turned on
 * 2026-10-17 for this tab -- see that registration's own comment for the real sync-bug fix that
 * had to go with it). A mob type absent from this map is "never engaged," not "level 0" -- there's
 * no fixed universe of trackable mob types to pad the list out with (Huntsman hooks any hostile
 * {@code instanceof Enemy}, not a fixed registry, per {@code HuntsmanListener}'s own doc).
 */
public final class HunterJournalTab implements NuclyusMenu {

    private static final int MARGIN = 10;
    private static final int ROW_HEIGHT = 12;
    private static final int NAME_COLOR = 0xFFE0E0E0;
    private static final int KILLS_COLOR = 0xFF9090FF;

    private record Row(String name, int level, int kills, Component tooltip) {
    }

    private List<Row> rows = List.of();
    private int scrollOffset;

    @Override
    public Component title() {
        return Component.literal("Hunter's Journal");
    }

    @Override
    public int order() {
        return 5;
    }

    @Override
    public void onShown() {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            rows = List.of();
            return;
        }
        PlayerHuntsman data = player.getData(ModAttachments.PLAYER_HUNTSMAN);
        List<Row> built = new ArrayList<>();
        for (Map.Entry<Identifier, Integer> entry : data.allKills().entrySet()) {
            Identifier mobType = entry.getKey();
            int kills = entry.getValue();
            if (kills <= 0) {
                continue;
            }
            int level = QuarryKnowledgeCurve.levelForKills(mobType, kills);
            String name = displayName(mobType);
            double bonusDamage = HuntsmanListener.DAMAGE_BONUS_PER_LEVEL * level;
            int cumulativeAtLevel = QuarryKnowledgeCurve.cumulativeKillsForLevel(mobType, level);
            int requiredForNext = QuarryKnowledgeCurve.killsRequiredForLevel(mobType, level + 1);
            int progressThisLevel = kills - cumulativeAtLevel;
            Component tooltip = Component.literal(String.format(Locale.ROOT,
                    "Level %d -- +%.1f damage per hit. %d/%d kills to next level.",
                    level, bonusDamage, progressThisLevel, requiredForNext));
            built.add(new Row(name, level, kills, tooltip));
        }
        built.sort((a, b) -> Integer.compare(b.kills(), a.kills()));
        rows = built;
        scrollOffset = 0;
    }

    private static String displayName(Identifier mobType) {
        return BuiltInRegistries.ENTITY_TYPE.get(mobType)
                .map(ref -> ref.value().getDescription().getString())
                .orElse(mobType.toString());
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
            graphics.text(font, Component.literal("No mobs engaged yet."), left, top, NAME_COLOR, false);
            return;
        }

        graphics.enableScissor(left, top, right, bottom);
        int y = top - scrollOffset;
        Component hoveredTooltip = null;
        for (Row row : rows) {
            if (y + ROW_HEIGHT >= top && y <= bottom) {
                String line = row.name() + " -- Level " + row.level();
                graphics.text(font, Component.literal(line), left, y, NAME_COLOR, false);
                String killsText = row.kills() + " kills";
                int killsX = right - font.width(killsText);
                graphics.text(font, Component.literal(killsText), killsX, y, KILLS_COLOR, false);
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
