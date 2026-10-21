package com.github.cerealklla.lyfe.mana.client;

import com.github.cerealklla.lyfe.LyfeMod;
import com.github.cerealklla.lyfe.mana.EntityMana;
import com.github.cerealklla.lyfe.registration.ModAttachments;
import com.github.cerealklla.lyfe.registration.ModAttributes;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.neoforged.neoforge.client.gui.GuiLayer;

/**
 * Player Mana HUD (design doc, 2026-10-20: "Represented on player HUD... by a blue water droplet
 * icon, 1 for every 10 Mana. Depleted looks like a small blue dot"). No vanilla bar exists to
 * replace (unlike {@code hunger.HungerOverlay}/{@code swim.SwimmerOverlay}), so this registers as
 * a brand-new layer via {@code RegisterGuiLayersEvent#registerAboveAll} rather than {@code
 * replaceLayer}. Originally shipped as plain filled squares (no droplet art existed yet); now uses
 * real one-off generated droplet sprites -- see this class's own fields for the real request.
 *
 * <p>Anchored to the real left edge of the screen (not vanilla's hotbar-centered reference point --
 * see {@code xLeft}'s own doc) and, per explicit request, never auto-hides even at full Mana.</p>
 */
public final class ManaOverlay implements GuiLayer {

    private static final int ICON_SIZE = 9;
    private static final int ICON_SPACING = 9;
    private static final int ICONS_PER_ROW = 10;
    private static final int ROW_SPACING = 10;
    private static final int MARGIN_LEFT = 10;
    // Real report, 2026-10-20 (screenshot): "it's directly in line with chat text right now." Was
    // 49 -- dropped it below chat's own default bottom-left text line.
    private static final int MARGIN_BOTTOM = 36;

    // Real follow-up report, 2026-10-21 (screenshot): "This still covers the text log" -- the single-
    // row position fix above only ever addressed the base row's own height; it never bounded how far
    // the grid climbs as max Mana grows (each 10 Mana adds another icon, every 10 icons adds another
    // row stacking upward via `yBase - row * ROW_SPACING`). Both this skill's own Magician XP (up to
    // +250 Max Mana at level 25) and several other still-stacking sources (Nature Staff tier, Crafting
    // Quality, future Runes) all grow the SAME pool, so any fixed row count eventually climbs back into
    // chat's own territory again. Capped hard at MAX_ICON_ROWS -- height can never grow past this
    // regardless of how high Max Mana eventually goes; anything beyond is shown as a compact "current/
    // max" numeric readout instead of more icons.
    private static final int MAX_ICON_ROWS = 1;
    private static final int OVERFLOW_TEXT_COLOR = 0xFFAAAAFF;

    // Real request, 2026-10-20: "can't we do water droplets? ... there isn't a reason to do it on
    // the HUD too" (re: the placeholder filled-square icons, originally shipped square-only since no
    // art existed yet -- the NPC-nameplate text bar is genuinely stuck with plain characters, but the
    // HUD can render real textures same as Hunger/Air's own vanilla sprites). Generated as a small,
    // one-off pixel-art droplet (same "no Python/Node available, use a one-off Java program" pipeline
    // this suite already uses for its other generated art, e.g. the Recallcinite Totem's recolor) --
    // see context/decisions.md for the generator. A partial (mid-10) icon reuses the FULL droplet at
    // a smaller inset scale over the EMPTY droplet, mirroring the old inset-square trick with real art.
    private static final Identifier FULL_SPRITE = Identifier.fromNamespaceAndPath(LyfeMod.MODID, "hud/mana_full");
    private static final Identifier EMPTY_SPRITE = Identifier.fromNamespaceAndPath(LyfeMod.MODID, "hud/mana_empty");
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
        if (maxMana <= 0.0) {
            return;
        }
        // Explicit user request, 2026-10-20: "do not make the mana bar auto-hide on the HUD, it
        // should be always visible" -- deliberately does NOT mirror Hunger/Air's own "hide while
        // fully topped up" behavior (that was an assumption carried over from the wrong precedent,
        // never actually asked for here).

        int iconCount = (int) Math.ceil(maxMana / 10.0);
        int fullIcons = (int) (currentMana / 10.0);
        boolean partialDot = currentMana % 10.0 != 0;

        // Real report, 2026-10-20 (screenshot): "Please move this to the left instead of in the
        // middle." The old `guiWidth() / 2 - 91` anchor was vanilla's own hotbar-centered reference
        // point (where Health/Hunger sit, just offset outward) -- correct for sitting *next to* those
        // bars, but still visually "in the middle" of the screen as a whole, not actually on the left
        // edge like the user wanted. Anchored to the real screen edge instead.
        int xLeft = MARGIN_LEFT;
        int yBase = guiGraphics.guiHeight() - MARGIN_BOTTOM;

        // See MAX_ICON_ROWS' own doc -- height is capped regardless of how large maxMana gets.
        int maxVisibleIcons = MAX_ICON_ROWS * ICONS_PER_ROW;
        int visibleIconCount = Math.min(iconCount, maxVisibleIcons);

        for (int i = 0; i < visibleIconCount; i++) {
            int row = i / ICONS_PER_ROW;
            int col = i % ICONS_PER_ROW;
            int x = xLeft + col * ICON_SPACING;
            int y = yBase - row * ROW_SPACING;

            if (i < fullIcons) {
                guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, FULL_SPRITE, x, y, ICON_SIZE, ICON_SIZE);
            } else if (i == fullIcons && partialDot) {
                // "Depleted looks like a small blue dot" -- a smaller inset droplet over the empty one.
                guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, EMPTY_SPRITE, x, y, ICON_SIZE, ICON_SIZE);
                guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, FULL_SPRITE,
                        x + DOT_INSET, y + DOT_INSET, ICON_SIZE - DOT_INSET * 2, ICON_SIZE - DOT_INSET * 2);
            } else {
                guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, EMPTY_SPRITE, x, y, ICON_SIZE, ICON_SIZE);
            }
        }

        // Max Mana outgrew what the capped grid can show as icons -- fall back to a compact numeric
        // readout instead of ever drawing another row (see MAX_ICON_ROWS' own doc).
        if (iconCount > maxVisibleIcons) {
            String readout = (int) currentMana + "/" + (int) maxMana;
            int textY = yBase - (MAX_ICON_ROWS - 1) * ROW_SPACING - ICON_SIZE - 2;
            guiGraphics.text(Minecraft.getInstance().font, Component.literal(readout), xLeft, textY, OVERFLOW_TEXT_COLOR, true);
        }
    }
}
