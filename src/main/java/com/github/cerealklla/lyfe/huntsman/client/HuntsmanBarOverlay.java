package com.github.cerealklla.lyfe.huntsman.client;

import java.util.List;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.neoforged.neoforge.client.gui.GuiLayer;

/**
 * Transient Quarry Knowledge HUD (explicit user spec, 2026-10-16: "I'd like to use the same visual
 * style researcher uses for it's knowledge bars") -- a line-for-line copy of {@code
 * research.client.ResearchBarOverlay}'s own layout/colors/positioning, just reading {@link
 * ClientHuntsmanBarState} and showing the mob's current Quarry Knowledge level (not a percentage,
 * per explicit request) alongside its progress bar. Stacks in the same bottom-right corner, below
 * the Research bars (its own {@code BOTTOM_MARGIN} clears both the XP bar stack and the Research
 * bar stack above it).
 */
public final class HuntsmanBarOverlay implements GuiLayer {

    private static final int BAR_WIDTH = 180;
    private static final int BAR_HEIGHT = 5;
    private static final int GAP = 3;
    // Real live report, 2026-10-16: "slide the quarry knoweldge down a few pixels, i can't read
    // it" -- 135 sat too close above the Research bar stack (whose own BOTTOM_MARGIN=70 was sized
    // for a single active bar; this mod's Research bars can stack more than one at once, so a wider
    // Research stack could reach high enough to overlap this bar's text). Lowered toward the hotbar,
    // keeping the same ~25px single-bar clearance Research itself uses above the XP bar (70 - 45).
    private static final int BOTTOM_MARGIN = 95;
    private static final int RIGHT_MARGIN = 10;

    private static final int ACCENT_RGB = 0xFF5555;
    private static final int BACKGROUND_RGB = 0x101010;
    private static final int BORDER_RGB = 0x000000;
    private static final int TEXT_RGB = 0xFFFFFF;
    private static final int BACKGROUND_MAX_ALPHA = 160;

    @Override
    public void render(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker) {
        List<ClientHuntsmanBarState.RenderBar> bars = ClientHuntsmanBarState.activeBars();
        if (bars.isEmpty()) {
            return;
        }

        Font font = Minecraft.getInstance().font;
        int right = guiGraphics.guiWidth() - RIGHT_MARGIN;
        int left = right - BAR_WIDTH;
        int blockHeight = font.lineHeight + 2 + BAR_HEIGHT + 2;
        int bottom = guiGraphics.guiHeight() - BOTTOM_MARGIN;

        for (int i = 0; i < bars.size(); i++) {
            ClientHuntsmanBarState.RenderBar bar = bars.get(i);
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

            // Explicit user request, 2026-10-16: "the text should just say teh level, no %" -- the
            // fill bar itself still shows fractional progress visually, just the label doesn't.
            String label = "Quarry Knowledge: " + bar.displayName() + " (Lv" + bar.level() + ")";
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
