package com.github.cerealklla.lyfe.recallcinite.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.neoforged.neoforge.client.gui.GuiLayer;

/**
 * Transient progress bar shown while channeling the Recallcinite Totem's 10-second recall hold --
 * same bottom-right bordered/filled-bar visual treatment as {@code research.client.ResearchBarOverlay},
 * centered horizontally instead since there's only ever one of these active at a time (never a
 * stacked list) and it reads better front-and-center for a full-screen channel like this, closer to
 * vanilla's own experience/boss-bar placement.
 */
public final class RecallciniteChannelOverlay implements GuiLayer {

    private static final int BAR_WIDTH = 200;
    private static final int BAR_HEIGHT = 6;
    private static final int TOP_MARGIN = 60;

    private static final int ACCENT_RGB = 0x9B30FF;
    private static final int BACKGROUND_RGB = 0x101010;
    private static final int BORDER_RGB = 0x000000;
    private static final int TEXT_RGB = 0xE0C0FF;

    @Override
    public void render(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker) {
        if (!ClientRecallciniteState.isChannelActive()) {
            return;
        }
        float fraction = ClientRecallciniteState.channelFraction();

        Font font = Minecraft.getInstance().font;
        int centerX = guiGraphics.guiWidth() / 2;
        int left = centerX - BAR_WIDTH / 2;
        int right = centerX + BAR_WIDTH / 2;
        int barTop = TOP_MARGIN;
        int barBottom = barTop + BAR_HEIGHT;

        String label = "Recalling... (" + Math.round(fraction * 100) + "%)";
        int textWidth = font.width(label);
        guiGraphics.text(font, label, centerX - textWidth / 2, barTop - font.lineHeight - 2, TEXT_RGB | 0xFF000000);

        guiGraphics.fill(left - 1, barTop - 1, right + 1, barBottom + 1, BORDER_RGB | 0xFF000000);
        guiGraphics.fill(left, barTop, right, barBottom, BACKGROUND_RGB | 0xA0000000);
        int filledRight = left + Math.round((right - left) * fraction);
        if (filledRight > left) {
            guiGraphics.fill(left, barTop, filledRight, barBottom, ACCENT_RGB | 0xFF000000);
        }
    }
}
