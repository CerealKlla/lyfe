package com.github.cerealklla.lyfe.research.client;

import java.util.List;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.neoforged.neoforge.client.gui.GuiLayer;

/**
 * Transient research-progress HUD (2026-10-03, user request) -- one bar per recipe currently being
 * researched (Research Bench or a Research Note), showing Research Points progress toward that
 * recipe's threshold. Same visual treatment as {@code xpbar.client.XpBarOverlay} (bottom-right
 * corner, moved off bottom-center the same day), just a larger {@code BOTTOM_MARGIN} so the two
 * stacks never overlap.
 */
public final class ResearchBarOverlay implements GuiLayer {

    private static final int BAR_WIDTH = 180;
    private static final int BAR_HEIGHT = 5;
    private static final int GAP = 3;
    private static final int BOTTOM_MARGIN = 70; // clears XpBarOverlay's own stack (45) plus its block height
    private static final int RIGHT_MARGIN = 10;

    private static final int ACCENT_RGB = 0x55AAFF;
    private static final int BACKGROUND_RGB = 0x101010;
    private static final int BORDER_RGB = 0x000000;
    private static final int TEXT_RGB = 0xFFFFFF;
    private static final int BACKGROUND_MAX_ALPHA = 160;

    @Override
    public void render(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker) {
        List<ClientResearchBarState.RenderBar> bars = ClientResearchBarState.activeBars();
        if (bars.isEmpty()) {
            return;
        }

        Font font = Minecraft.getInstance().font;
        int right = guiGraphics.guiWidth() - RIGHT_MARGIN;
        int left = right - BAR_WIDTH;
        int blockHeight = font.lineHeight + 2 + BAR_HEIGHT + 2;
        int bottom = guiGraphics.guiHeight() - BOTTOM_MARGIN;

        for (int i = 0; i < bars.size(); i++) {
            ClientResearchBarState.RenderBar bar = bars.get(i);
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

            String label = "Researching: " + bar.displayName() + " (" + Math.round(bar.fraction() * 100) + "%)";
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
