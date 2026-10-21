package com.github.cerealklla.lyfe.magician.client;

import java.util.List;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.neoforged.neoforge.client.gui.GuiLayer;

/**
 * Transient Spell Proficiency HUD (explicit user spec, 2026-10-21: "this should look visually like
 * XP gains, same as the popup with Quarry Knowledge and every other similar mechanic we have") -- a
 * line-for-line copy of {@code huntsman.client.HuntsmanBarOverlay}'s own layout/colors/positioning,
 * just reading {@link ClientSpellProficiencyBarState} and keyed by spell instead of mob type. Stacks
 * in the same bottom-right corner, above the Huntsman bar stack (its own {@code BOTTOM_MARGIN}
 * clears the XP bar stack, the Research bar stack, and the Huntsman bar stack beneath it).
 */
public final class SpellProficiencyBarOverlay implements GuiLayer {

    private static final int BAR_WIDTH = 180;
    private static final int BAR_HEIGHT = 5;
    private static final int GAP = 3;
    // Real report, 2026-10-21 (screenshot): overlapped the Location overlay sitting above it.
    // Lowered as far as it can go without colliding with HuntsmanBarOverlay's own block right below
    // it (95 + its ~18px block height leaves only ~7px of real slack before touching) -- if this
    // still isn't enough clearance from Location, the whole bar stack's spacing needs widening, not
    // just this one bar's margin.
    private static final int BOTTOM_MARGIN = 114;
    private static final int RIGHT_MARGIN = 10;

    private static final int ACCENT_RGB = 0x55AAFF;
    private static final int BACKGROUND_RGB = 0x101010;
    private static final int BORDER_RGB = 0x000000;
    private static final int TEXT_RGB = 0xFFFFFF;
    private static final int BACKGROUND_MAX_ALPHA = 160;

    @Override
    public void render(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker) {
        List<ClientSpellProficiencyBarState.RenderBar> bars = ClientSpellProficiencyBarState.activeBars();
        if (bars.isEmpty()) {
            return;
        }

        Font font = Minecraft.getInstance().font;
        int right = guiGraphics.guiWidth() - RIGHT_MARGIN;
        int left = right - BAR_WIDTH;
        int blockHeight = font.lineHeight + 2 + BAR_HEIGHT + 2;
        int bottom = guiGraphics.guiHeight() - BOTTOM_MARGIN;

        for (int i = 0; i < bars.size(); i++) {
            ClientSpellProficiencyBarState.RenderBar bar = bars.get(i);
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

            String label = "Spell Proficiency: " + bar.displayName() + " (Lv" + bar.level() + ")";
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
