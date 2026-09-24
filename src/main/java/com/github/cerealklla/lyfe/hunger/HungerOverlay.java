package com.github.cerealklla.lyfe.hunger;

import com.github.cerealklla.lyfe.api.Lyfe;
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
 * Replaces vanilla's hunger bar entirely (design doc Section 10.0) with one that can show up to
 * 30 icons as Survivalist's true max grows past vanilla's 10 -- rows wrap at 10 icons, same as
 * vanilla's own row width, growing upward. Reuses vanilla's own food-icon sprites for visual
 * consistency; only whole/half/empty granularity is drawn (no hunger-effect shake, no mob-effect
 * sprite swap) -- a simplification flagged in decisions.md, not a vanilla feature parity goal.
 */
public final class HungerOverlay implements GuiLayer {

    private static final Identifier EMPTY_SPRITE = Identifier.withDefaultNamespace("hud/food_empty");
    private static final Identifier HALF_SPRITE = Identifier.withDefaultNamespace("hud/food_half");
    private static final Identifier FULL_SPRITE = Identifier.withDefaultNamespace("hud/food_full");

    private static final int ICON_SIZE = 9;
    private static final int ICON_SPACING = 8;
    private static final int ICONS_PER_ROW = 10;
    private static final int ROW_SPACING = 10;
    private static final int MARGIN_RIGHT = 10;
    private static final int MARGIN_BOTTOM = 39;

    @Override
    public void render(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        PlayerHunger hunger = player.getData(ModAttachments.PLAYER_HUNGER);
        int trueHunger = hunger.getTrueHunger();
        int survivalistLevel = Lyfe.getLevel(player, Skills.SURVIVALIST_ID);
        int growth = HungerConstants.MAX_HUNGER_AT_MAX_LEVEL - HungerConstants.BASE_MAX_HUNGER;
        int currentMax = HungerConstants.BASE_MAX_HUNGER + growth * survivalistLevel / Skills.MAX_LEVEL;

        int iconCount = currentMax / 2;
        int xRight = guiGraphics.guiWidth() / 2 + 91 - MARGIN_RIGHT;
        int yBase = guiGraphics.guiHeight() - MARGIN_BOTTOM;

        for (int i = 0; i < iconCount; i++) {
            int row = i / ICONS_PER_ROW;
            int col = i % ICONS_PER_ROW;
            int x = xRight - col * ICON_SPACING - ICON_SIZE;
            int y = yBase - row * ROW_SPACING;

            guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, EMPTY_SPRITE, x, y, ICON_SIZE, ICON_SIZE);
            if (i * 2 + 1 < trueHunger) {
                guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, FULL_SPRITE, x, y, ICON_SIZE, ICON_SIZE);
            } else if (i * 2 + 1 == trueHunger) {
                guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, HALF_SPRITE, x, y, ICON_SIZE, ICON_SIZE);
            }
        }
    }
}
