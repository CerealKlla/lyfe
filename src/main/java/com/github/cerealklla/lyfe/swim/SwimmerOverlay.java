package com.github.cerealklla.lyfe.swim;

import com.github.cerealklla.lyfe.api.Lyfe;
import com.github.cerealklla.lyfe.hunger.HungerOverlay;
import com.github.cerealklla.lyfe.registration.ModAttachments;
import com.github.cerealklla.lyfe.skill.Skills;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.gui.GuiLayer;

/**
 * Replaces vanilla's air-bubble bar entirely (design doc Section 5) with one that can show up to
 * 30 bubbles as Swimmer's true max grows past vanilla's 10 -- same row-wrap-at-10 shape
 * {@code .hunger.HungerOverlay} already established for its own 30-icon cap. Reuses vanilla's own
 * air-bubble sprites (confirmed against the decompiled {@code Gui} source: {@code hud/air},
 * {@code hud/air_bursting}, {@code hud/air_empty}) for visual consistency; unlike hunger, vanilla's
 * own air display has no half-state sprite, so each bubble is simply present/absent -- the one
 * granularity simplification this overlay needs versus {@code HungerOverlay}'s half-icon case.
 */
public final class SwimmerOverlay implements GuiLayer {

    private static final Identifier FULL_SPRITE = Identifier.withDefaultNamespace("hud/air");
    private static final Identifier POPPING_SPRITE = Identifier.withDefaultNamespace("hud/air_bursting");
    private static final Identifier EMPTY_SPRITE = Identifier.withDefaultNamespace("hud/air_empty");

    private static final int BUBBLE_SIZE = 9;
    private static final int BUBBLE_SPACING = 8;
    private static final int BUBBLES_PER_ROW = 10;
    private static final int ROW_SPACING = 10;
    private static final int MARGIN_RIGHT = 10;

    @Override
    public void render(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        PlayerAir air = player.getData(ModAttachments.PLAYER_AIR);
        int swimmerLevel = Lyfe.getLevel(player, Skills.SWIMMER_ID);
        int growth = AirConstants.MAX_AIR_AT_MAX_LEVEL - AirConstants.BASE_MAX_AIR;
        int currentMax = AirConstants.BASE_MAX_AIR + growth * swimmerLevel / Skills.MAX_LEVEL;

        boolean notFull = air.getTrueAir() < currentMax;
        if (!notFull) {
            return; // Matches vanilla's own behavior: no bar shown at all while fully topped up.
        }

        int bubbleCount = currentMax / AirConstants.TICKS_PER_BUBBLE;
        int fullBubbles = air.getTrueAir() / AirConstants.TICKS_PER_BUBBLE;
        boolean hasPartialPopping = air.getTrueAir() % AirConstants.TICKS_PER_BUBBLE != 0;

        // Dynamic, not a fixed one-row offset (real bug, 2026-10-09: overlapped HungerOverlay the
        // moment Survivalist's growing max hunger wrapped onto a second/third row) -- always sits
        // exactly one row above however many rows HungerOverlay is currently actually drawing.
        int marginBottom = HungerOverlay.MARGIN_BOTTOM + HungerOverlay.currentRowCount(player) * HungerOverlay.ROW_SPACING;
        int xRight = guiGraphics.guiWidth() / 2 + 91 - MARGIN_RIGHT;
        int yBase = guiGraphics.guiHeight() - marginBottom;

        for (int i = 0; i < bubbleCount; i++) {
            int row = i / BUBBLES_PER_ROW;
            int col = i % BUBBLES_PER_ROW;
            int x = xRight - col * BUBBLE_SPACING - BUBBLE_SIZE;
            int y = yBase - row * ROW_SPACING;

            if (i < fullBubbles) {
                guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, FULL_SPRITE, x, y, BUBBLE_SIZE, BUBBLE_SIZE);
            } else if (i == fullBubbles && hasPartialPopping) {
                guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, POPPING_SPRITE, x, y, BUBBLE_SIZE, BUBBLE_SIZE);
            } else {
                guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, EMPTY_SPRITE, x, y, BUBBLE_SIZE, BUBBLE_SIZE);
            }
        }
    }
}
