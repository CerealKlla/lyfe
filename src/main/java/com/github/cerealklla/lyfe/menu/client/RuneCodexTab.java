package com.github.cerealklla.lyfe.menu.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.github.cerealklla.lyfe.registration.ModAttachments;
import com.github.cerealklla.lyfe.socket.RuneCatalog;
import com.github.cerealklla.lyfe.socket.RuneId;
import com.github.cerealklla.lyfe.socket.RunewordCatalog;
import com.github.cerealklla.lyfe.socket.RunewordDefinition;
import com.github.cerealklla.lyfe.socket.RunewordRollsPayload;
import com.github.cerealklla.lyfe.socket.RequestRunewordRollsPayload;
import com.github.cerealklla.nuclyus.client.TextWrap;
import com.github.cerealklla.nuclyus.menu.client.NuclyusMenu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;

/**
 * "Rune Codex" tab -- two sub-tabs (explicit user spec): every Rune ever found, and every Runeword
 * fully discovered. Reuses the same tab-bar idea the host menu itself uses, just a small internal
 * one rather than sharing the host's own private implementation.
 *
 * <p>"Runes Found" reads the synced {@code PLAYER_DISCOVERED_RUNES} attachment directly -- no
 * network round trip needed. "Runewords Discovered" needs one extra piece the client doesn't
 * otherwise have: each {@link RunewordDefinition}'s real server-rolled {@link RuneId} sequence
 * (the definitions themselves are fixed, identical-on-both-sides static data, but the actual
 * sequence is server-specific) -- requested once via {@link RequestRunewordRollsPayload} the first
 * time this sub-tab is shown, cached in {@link #rolls} thereafter. A Runeword counts as discovered
 * the moment every *distinct* RuneId its sequence needs has been found at least once -- not
 * simultaneous possession, and not actual assembly on an item -- computed fresh each render from
 * the synced discovered-set, never stored as its own flag.
 */
public final class RuneCodexTab implements NuclyusMenu {

    private static final int MARGIN = 10;
    private static final int SUB_TAB_HEIGHT = 20;
    private static final int ROW_HEIGHT = 12;
    private static final int SUB_TAB_BG = 0xFF373737;
    private static final int SUB_TAB_ACTIVE_BG = 0xFFC6C6C6;
    private static final int SUB_TAB_TEXT_COLOR = 0xFF202020;
    private static final int ITEM_COLOR = 0xFF7FD17F;
    private static final int DIM_COLOR = 0xFFA0A0A0;

    private static List<RunewordRollsPayload.Entry> rolls = List.of();
    private static boolean rolledRequested;

    /** Written by {@code LyfeMod}'s payload handler for {@link RunewordRollsPayload}. */
    public static void setRolls(List<RunewordRollsPayload.Entry> newRolls) {
        rolls = newRolls;
    }

    private int subTab;
    private int scrollOffset;

    @Override
    public Component title() {
        return Component.literal("Rune Codex");
    }

    @Override
    public int order() {
        return 7;
    }

    @Override
    public void onShown() {
        scrollOffset = 0;
        if (!rolledRequested) {
            rolledRequested = true;
            var connection = Minecraft.getInstance().getConnection();
            if (connection != null) {
                connection.send(new ServerboundCustomPayloadPacket(new RequestRunewordRollsPayload()));
            }
        }
    }

    @Override
    public void onHidden() {
    }

    private Set<RuneId> discoveredRunes() {
        var player = Minecraft.getInstance().player;
        return player == null ? Set.of() : player.getData(ModAttachments.PLAYER_DISCOVERED_RUNES).discoveredRunes();
    }

    private Rect2i subTabRect(Rect2i area, int index) {
        int w = area.getWidth() / 2;
        return new Rect2i(area.getX() + index * w, area.getY(), w, SUB_TAB_HEIGHT);
    }

    @Override
    public boolean mouseClicked(Rect2i area, MouseButtonEvent event, boolean doubleClick) {
        if (event.y() < area.getY() + SUB_TAB_HEIGHT) {
            int index = event.x() < area.getX() + area.getWidth() / 2 ? 0 : 1;
            if (index != subTab) {
                subTab = index;
                scrollOffset = 0;
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(Rect2i area, double mouseX, double mouseY, double scrollX, double scrollY) {
        int contentHeight = subTab == 0 ? discoveredRunes().size() * ROW_HEIGHT : RunewordCatalog.all().size() * ROW_HEIGHT * 2;
        int viewport = area.getHeight() - SUB_TAB_HEIGHT - 2 * MARGIN;
        int maxScroll = Math.max(0, contentHeight - viewport);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) (scrollY * ROW_HEIGHT * 2)));
        return true;
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, Rect2i area, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;

        for (int i = 0; i < 2; i++) {
            Rect2i rect = subTabRect(area, i);
            graphics.fill(rect.getX(), rect.getY(), rect.getX() + rect.getWidth(), rect.getY() + rect.getHeight(),
                    i == subTab ? SUB_TAB_ACTIVE_BG : SUB_TAB_BG);
            String label = i == 0 ? "Runes Found" : "Runewords Discovered";
            graphics.text(font, Component.literal(label),
                    rect.getX() + (rect.getWidth() - font.width(label)) / 2,
                    rect.getY() + (SUB_TAB_HEIGHT - font.lineHeight) / 2,
                    i == subTab ? SUB_TAB_TEXT_COLOR : 0xFFFFFFFF, false);
        }

        int left = area.getX() + MARGIN;
        int top = area.getY() + SUB_TAB_HEIGHT + MARGIN;
        int bottom = area.getY() + area.getHeight() - MARGIN;
        int right = area.getX() + area.getWidth() - MARGIN;

        graphics.enableScissor(left, top, right, bottom);
        if (subTab == 0) {
            renderRunesFound(graphics, font, left, top, bottom, right, mouseX, mouseY);
        } else {
            renderRunewords(graphics, font, left, top, bottom, right, mouseX, mouseY);
        }
        graphics.disableScissor();
    }

    private void renderRunesFound(GuiGraphicsExtractor graphics, Font font, int left, int top, int bottom, int right, int mouseX, int mouseY) {
        Set<RuneId> discovered = discoveredRunes();
        if (discovered.isEmpty()) {
            graphics.text(font, Component.literal("No Runes found yet."), left, top, DIM_COLOR, false);
            return;
        }
        List<RuneId> sorted = new ArrayList<>(discovered);
        sorted.sort((a, b) -> a.displayName().compareTo(b.displayName()));
        int y = top - scrollOffset;
        Component hoveredTooltip = null;
        for (RuneId id : sorted) {
            if (y + ROW_HEIGHT >= top && y <= bottom) {
                String label = id.displayName() + " Rune";
                graphics.text(font, Component.literal(label), left, y, ITEM_COLOR, false);
                if (mouseX >= left && mouseX < right && mouseY >= y && mouseY < y + ROW_HEIGHT) {
                    RuneCatalog.RuneStat stat = RuneCatalog.of(id);
                    if (stat != null) {
                        String sign = stat.amount() >= 0 ? "+" : "";
                        hoveredTooltip = Component.literal(sign + stat.amount() + " " + stat.shortLabel());
                    }
                }
            }
            y += ROW_HEIGHT;
        }
        if (hoveredTooltip != null) {
            graphics.setTooltipForNextFrame(font, hoveredTooltip, mouseX, mouseY);
        }
    }

    private void renderRunewords(GuiGraphicsExtractor graphics, Font font, int left, int top, int bottom, int right, int mouseX, int mouseY) {
        Set<RuneId> discovered = discoveredRunes();
        List<RunewordDefinition> discoveredWords = new ArrayList<>();
        for (RunewordDefinition def : RunewordCatalog.all()) {
            List<RuneId> sequence = sequenceFor(def.name());
            if (!sequence.isEmpty() && discovered.containsAll(Set.copyOf(sequence))) {
                discoveredWords.add(def);
            }
        }
        if (discoveredWords.isEmpty()) {
            graphics.text(font, Component.literal("No Runewords discovered yet."), left, top, DIM_COLOR, false);
            return;
        }
        int wrapWidth = right - left;
        int y = top - scrollOffset;
        for (RunewordDefinition def : discoveredWords) {
            List<RuneId> sequence = sequenceFor(def.name());
            String runesLine = sequence.stream().map(RuneId::displayName).reduce((a, b) -> a + " + " + b).orElse("");
            if (y + ROW_HEIGHT >= top && y <= bottom) {
                graphics.text(font, Component.literal("\"" + def.name() + "\" (" + runesLine + ")"), left, y, ITEM_COLOR, false);
            }
            y += ROW_HEIGHT;
            for (String line : TextWrap.wrap(font, def.description(), wrapWidth)) {
                if (y + ROW_HEIGHT >= top && y <= bottom) {
                    graphics.text(font, Component.literal(line), left, y, DIM_COLOR, false);
                }
                y += ROW_HEIGHT;
            }
            // Same "which item type this needs" disclosure as the item's own hover tooltip
            // (LyfeModClient#onSocketTooltip) -- shared via EquipmentCategory#displayLabel so the
            // two screens can't drift apart again.
            if (y + ROW_HEIGHT >= top && y <= bottom) {
                graphics.text(font, Component.literal(def.category().displayLabel() + " Only"), left, y, DIM_COLOR, false);
            }
            y += ROW_HEIGHT;
        }
    }

    private static List<RuneId> sequenceFor(String runewordName) {
        for (RunewordRollsPayload.Entry entry : rolls) {
            if (entry.name().equals(runewordName)) {
                return entry.sequence();
            }
        }
        return List.of();
    }

}
