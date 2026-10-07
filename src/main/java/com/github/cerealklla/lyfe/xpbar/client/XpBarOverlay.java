package com.github.cerealklla.lyfe.xpbar.client;

import java.util.List;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.neoforged.neoforge.client.gui.GuiLayer;

/**
 * Transient XP bar HUD -- one bar per skill currently gaining XP, replacing the old chat-message
 * announcements. Anchored bottom-right (moved off bottom-center 2026-10-03, user request), stacked
 * upward from the corner. Positioning/ordering for this first pass is this mod's own call, not
 * specified by the user: alphabetical by display name (see {@code ClientXpBarState#activeBars}),
 * lowest/newest-looking slot closest to the corner.
 *
 * <p>Renders purely from {@link ClientXpBarState} -- no networking of its own, same split every
 * other overlay in this mod already uses.
 */
public final class XpBarOverlay implements GuiLayer {

    private static final int BAR_WIDTH = 180;
    private static final int BAR_HEIGHT = 5;
    private static final int GAP = 3;
    // Placeholder/tunable, not derived from any vanilla constant -- just eyeballed clearance from
    // the corner.
    private static final int BOTTOM_MARGIN = 45;
    private static final int RIGHT_MARGIN = 10;

    private static final int ACCENT_RGB = 0x55FF55;
    private static final int BACKGROUND_RGB = 0x101010;
    private static final int BORDER_RGB = 0x000000;
    private static final int TEXT_RGB = 0xFFFFFF;
    private static final int BACKGROUND_MAX_ALPHA = 160; // slightly translucent even at full visibility

    @Override
    public void render(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker) {
        List<ClientXpBarState.RenderBar> bars = ClientXpBarState.activeBars();
        if (bars.isEmpty()) {
            return;
        }

        Font font = Minecraft.getInstance().font;
        int right = guiGraphics.guiWidth() - RIGHT_MARGIN;
        int left = right - BAR_WIDTH;
        int blockHeight = font.lineHeight + 2 + BAR_HEIGHT + 2;
        int bottom = guiGraphics.guiHeight() - BOTTOM_MARGIN;

        for (int i = 0; i < bars.size(); i++) {
            ClientXpBarState.RenderBar bar = bars.get(i);
            int blockBottom = bottom - i * (blockHeight + GAP);
            int textY = blockBottom - blockHeight;
            int barTop = textY + font.lineHeight + 2;
            int barBottom = barTop + BAR_HEIGHT;

            int alpha255 = Math.round(bar.alpha() * 255.0f) & 0xFF;
            if (alpha255 <= 0) {
                continue;
            }
            int textColor = (alpha255 << 24) | TEXT_RGB;
            int borderColor = (alpha255 << 24) | BORDER_RGB;
            int backgroundColor = (Math.min(alpha255, BACKGROUND_MAX_ALPHA) << 24) | BACKGROUND_RGB;
            int fillColor = (alpha255 << 24) | ACCENT_RGB;

            String label = bar.displayName() + " (Level " + bar.level() + ")";
            int textWidth = font.width(label);
            guiGraphics.text(font, label, right - textWidth, textY, textColor);

            guiGraphics.fill(left - 1, barTop - 1, right + 1, barBottom + 1, borderColor);
            guiGraphics.fill(left, barTop, right, barBottom, backgroundColor);
            int filledRight = left + (int) Math.round((right - left) * bar.fraction());
            if (filledRight > left) {
                guiGraphics.fill(left, barTop, filledRight, barBottom, fillColor);
            }
        }
    }
}
