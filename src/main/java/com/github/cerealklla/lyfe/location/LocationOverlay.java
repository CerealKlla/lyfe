package com.github.cerealklla.lyfe.location;

import java.util.Optional;

import com.github.cerealklla.lyfe.minimap.MinimapOverlay;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.neoforged.neoforge.client.gui.GuiLayer;

/**
 * Persistent top-right overlay -- a fixed two-line display (design doc Section 9.3-adjacent; see
 * decisions.md, 2026-09-26) backed by the player's own knowledge (see {@link LocationTracker}),
 * not raw world truth. Moved here from Cartographyr 2026-09-24 -- see decisions.md. Renders
 * nothing until the first {@link LocationPayload} arrives, and renders nothing again whenever the
 * most recent payload has neither line -- i.e. it genuinely reflects "currently in no known
 * territory," not just whatever was last detected.
 *
 * <p><b>Reworked from a generic per-layer stack to this fixed 2-line format, 2026-09-26</b>
 * (explicit user request) -- line 1 is the resolved place's display name (settlement or natural
 * region); line 2 is "Town Proper"/"No Man's Land"/"Outskirts"/a plot's name, or absent. Either
 * line can be present without the other (e.g. a settlement with no plots nearby shows only line 1
 * plus whichever of the fixed zone words applies) -- both are rendered independently rather than
 * assuming they're always paired.
 *
 * <p><b>Anchored beneath the minimap, 2026-09-29</b> (explicit user request, see {@code
 * minimap.MinimapOverlay}'s own doc) -- its top offset is {@link MinimapOverlay#RESERVED_HEIGHT}
 * plus this class's own {@link #MARGIN}, not just {@code MARGIN} alone, so the two stack as one
 * top-right column regardless of the minimap's own size.
 */
public final class LocationOverlay implements GuiLayer {

    private static final int MARGIN = 6;
    private static final int PADDING = 4;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int BACKGROUND_COLOR = 0xE0101010; // mostly-solid dark backing, so it stands out over any scene

    @Override
    public void render(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker) {
        Optional<String> line1 = ClientLocationState.getLine1();
        Optional<String> line2 = ClientLocationState.getLine2();
        if (line1.isEmpty() && line2.isEmpty()) {
            return;
        }

        Font font = Minecraft.getInstance().font;
        String[] texts = line1.isPresent() && line2.isPresent()
                ? new String[] {line1.get(), line2.get()}
                : line1.isPresent() ? new String[] {line1.get()} : new String[] {line2.get()};

        int maxWidth = 0;
        for (String text : texts) {
            maxWidth = Math.max(maxWidth, font.width(text));
        }
        int lineHeight = font.lineHeight;

        int right = guiGraphics.guiWidth() - MARGIN;
        int top = MinimapOverlay.RESERVED_HEIGHT + MARGIN;
        int left = right - maxWidth - PADDING * 2;
        int bottom = top + lineHeight * texts.length + PADDING * 2;

        guiGraphics.fill(left, top, right, bottom, BACKGROUND_COLOR);
        for (int i = 0; i < texts.length; i++) {
            guiGraphics.text(font, texts[i], left + PADDING, top + PADDING + i * lineHeight, TEXT_COLOR);
        }
    }
}
