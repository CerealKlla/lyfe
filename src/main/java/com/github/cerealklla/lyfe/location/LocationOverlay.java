package com.github.cerealklla.lyfe.location;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.neoforged.neoforge.client.gui.GuiLayer;

/**
 * Persistent top-right "Location: <name>" overlay, backed by the player's own knowledge (see
 * {@link LocationTracker}), not raw world truth. Moved here from Cartographyr 2026-09-24 -- see
 * decisions.md. Renders nothing until the first {@link LocationPayload} arrives, then always shows
 * the most recently received name (never blanks again on its own).
 */
public final class LocationOverlay implements GuiLayer {

    private static final int MARGIN = 6;
    private static final int PADDING = 4;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int BACKGROUND_COLOR = 0xE0101010; // mostly-solid dark backing, so it stands out over any scene

    @Override
    public void render(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker) {
        String name = ClientLocationState.get();
        if (name == null || name.isEmpty()) {
            return;
        }

        Font font = Minecraft.getInstance().font;
        String text = "Location: " + name;
        int textWidth = font.width(text);
        int textHeight = font.lineHeight;

        int right = guiGraphics.guiWidth() - MARGIN;
        int top = MARGIN;
        int left = right - textWidth - PADDING * 2;
        int bottom = top + textHeight + PADDING * 2;

        guiGraphics.fill(left, top, right, bottom, BACKGROUND_COLOR);
        guiGraphics.text(font, text, left + PADDING, top + PADDING, TEXT_COLOR);
    }
}
