package com.github.cerealklla.lyfe.location;

import java.util.List;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.neoforged.neoforge.client.gui.GuiLayer;

/**
 * Persistent top-right overlay, one line per layer (e.g. "Location: X", and eventually a
 * "Territory: Y" line above it) -- backed by the player's own knowledge (see {@link
 * LocationTracker}), not raw world truth. Moved here from Cartographyr 2026-09-24 -- see
 * decisions.md. Renders nothing until the first {@link LocationPayload} arrives, then always shows
 * the most recently received lines (never blanks again on its own).
 *
 * <p>Reworked from a single line to a stack the same day (second pass), when Cartographyr's Layer
 * registry made "more than one line" a real possibility. Lines arrive already sorted by placement
 * (highest first) -- this only lays them out, it doesn't re-sort.
 */
public final class LocationOverlay implements GuiLayer {

    private static final int MARGIN = 6;
    private static final int PADDING = 4;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int BACKGROUND_COLOR = 0xE0101010; // mostly-solid dark backing, so it stands out over any scene

    @Override
    public void render(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker) {
        List<LocationPayload.LocationLine> lines = ClientLocationState.get();
        if (lines.isEmpty()) {
            return;
        }

        Font font = Minecraft.getInstance().font;
        String[] texts = new String[lines.size()];
        int maxWidth = 0;
        for (int i = 0; i < lines.size(); i++) {
            LocationPayload.LocationLine line = lines.get(i);
            texts[i] = line.label() + ": " + line.name();
            maxWidth = Math.max(maxWidth, font.width(texts[i]));
        }
        int lineHeight = font.lineHeight;

        int right = guiGraphics.guiWidth() - MARGIN;
        int top = MARGIN;
        int left = right - maxWidth - PADDING * 2;
        int bottom = top + lineHeight * lines.size() + PADDING * 2;

        guiGraphics.fill(left, top, right, bottom, BACKGROUND_COLOR);
        for (int i = 0; i < texts.length; i++) {
            guiGraphics.text(font, texts[i], left + PADDING, top + PADDING + i * lineHeight, TEXT_COLOR);
        }
    }
}
