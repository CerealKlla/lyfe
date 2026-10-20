package com.github.cerealklla.lyfe.mana.client;

import com.github.cerealklla.lyfe.mana.EntityMana;
import com.github.cerealklla.lyfe.registration.ModAttachments;
import com.github.cerealklla.lyfe.registration.ModAttributes;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.neoforged.neoforge.client.gui.GuiLayer;

/**
 * Player Mana HUD (design doc, 2026-10-20: "Represented on player HUD... by a blue water droplet
 * icon, 1 for every 10 Mana. Depleted looks like a small blue dot"). No vanilla bar exists to
 * replace (unlike {@code hunger.HungerOverlay}/{@code swim.SwimmerOverlay}), so this registers as
 * a brand-new layer via {@code RegisterGuiLayersEvent#registerAboveAll} rather than {@code
 * replaceLayer}. Drawn with plain filled squares rather than a new sprite texture -- no "blue
 * droplet" art exists anywhere in this codebase yet, and this suite's own established precedent
 * (e.g. the Guard renderer's reskinned-zombie placeholder) is to ship something functionally
 * correct first rather than block on real art.
 *
 * <p>Positioned above the vanilla Health bar on the left side (mirroring Hunger/Air's own
 * right-side stacking, just mirrored to the other side since Mana has no existing row to replace).
 * Uses a fixed margin rather than {@code HungerOverlay}/{@code SwimmerOverlay}'s dynamic
 * row-counting trick -- vanilla's own Health bar can also wrap to multiple rows once max health
 * grows past 10 hearts (Heartiness), which could in principle still collide with a high max-health
 * player; flagged as a known follow-up rather than guessed at blind, same as this suite's many
 * other HUD overlays that needed a live-test round to get positioning exactly right.
 */
public final class ManaOverlay implements GuiLayer {

    private static final int ICON_SIZE = 7;
    private static final int ICON_SPACING = 8;
    private static final int ICONS_PER_ROW = 10;
    private static final int ROW_SPACING = 10;
    private static final int MARGIN_LEFT = 10;
    private static final int MARGIN_BOTTOM = 49;

    private static final int FULL_COLOR = 0xFF1E90FF;
    private static final int EMPTY_COLOR = 0xFF0D2A4A;
    private static final int DOT_INSET = 2;

    @Override
    public void render(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        AttributeInstance maxManaAttribute = player.getAttribute(ModAttributes.MAX_MANA);
        if (maxManaAttribute == null) {
            return;
        }
        double maxMana = maxManaAttribute.getValue();
        EntityMana mana = player.getData(ModAttachments.ENTITY_MANA);
        double currentMana = mana.getCurrentMana();
        if (maxMana <= 0.0 || currentMana >= maxMana) {
            return; // Matches Hunger/Air's own behavior: no bar at all while fully topped up.
        }

        int iconCount = (int) Math.ceil(maxMana / 10.0);
        int fullIcons = (int) (currentMana / 10.0);
        boolean partialDot = currentMana % 10.0 != 0;

        int xLeft = guiGraphics.guiWidth() / 2 - 91 + MARGIN_LEFT;
        int yBase = guiGraphics.guiHeight() - MARGIN_BOTTOM;

        for (int i = 0; i < iconCount; i++) {
            int row = i / ICONS_PER_ROW;
            int col = i % ICONS_PER_ROW;
            int x = xLeft + col * ICON_SPACING;
            int y = yBase - row * ROW_SPACING;

            if (i < fullIcons) {
                guiGraphics.fill(x, y, x + ICON_SIZE, y + ICON_SIZE, FULL_COLOR);
            } else if (i == fullIcons && partialDot) {
                // "Depleted looks like a small blue dot" -- a smaller inset square inside the empty slot.
                guiGraphics.fill(x, y, x + ICON_SIZE, y + ICON_SIZE, EMPTY_COLOR);
                guiGraphics.fill(x + DOT_INSET, y + DOT_INSET, x + ICON_SIZE - DOT_INSET, y + ICON_SIZE - DOT_INSET, FULL_COLOR);
            } else {
                guiGraphics.fill(x, y, x + ICON_SIZE, y + ICON_SIZE, EMPTY_COLOR);
            }
        }
    }
}
