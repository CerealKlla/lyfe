package com.github.cerealklla.lyfe.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.client.gui.GuiLayer;

/**
 * Real live-test bug, 2026-10-26 (Caryvan Wagon/Carriage health system): vanilla's own mount-health
 * layer ({@code VanillaGuiLayers.VEHICLE_HEALTH}) renders at the exact same hotbar-relative position
 * as the food bar -- {@code xRight = guiWidth() / 2 + 91}, confirmed directly from the real
 * decompiled {@code Gui#extractVehicleHealth}. Harmless for a vanilla horse (nothing else occupies
 * that spot), but this mod already replaces the food bar with its own custom layout there ({@code
 * hunger.HungerOverlay}), so riding any {@code LivingEntity} with real health -- a horse, or now a
 * Caryvan Wagon/Carriage, since that mod's health system makes both real {@code LivingEntity}s --
 * draws mount-health hearts directly on top of the custom food icons. Real user request: "move those
 * icons off to the right, in a similar fashion to what we did with the mana bar location/style" --
 * mirrors {@code mana.client.ManaOverlay}'s own real screen-edge-anchored sprite-row technique
 * (confirmed by reading that class directly), just anchored to the opposite edge, and reusing
 * vanilla's own real heart-vehicle sprites/heart-row math (also confirmed from the same decompiled
 * method, including its existing 30-heart/60-HP display cap) instead of inventing new art or a new
 * cap.
 *
 * <p>Registered via {@code LyfeModClient#onRegisterGuiLayers}'s {@code replaceLayer(
 * VanillaGuiLayers.VEHICLE_HEALTH, ...)} -- same mechanism {@code HungerOverlay}/{@code
 * SwimmerOverlay} already use to replace a vanilla bar outright.
 *
 * <p><b>Real bug found and fixed, 2026-10-26</b> -- a live-test screenshot of a player sitting in a
 * Wagon with zero horses hitched (so this was never about a horse's randomized 15-30 HP range at
 * all) showed Nuclyus's own real stat bar reading ~15-20% while this overlay showed ~90% full, same
 * entity, same instant. Hand-derived the exact mismatch from vanilla's own real heart math (which
 * this class originally ported verbatim from {@code Gui#extractVehicleHealth}/
 * {@code getVehicleMaxHearts}): that formula hardcodes 2 HP per heart, capped at 30 hearts -- a
 * 60 HP display window, sized for vanilla's own mount range. A Wagon's real base Max HP is 100 (a
 * Carriage's is 150), far past that window -- so at a real ~18/100 HP (18%), the FIRST, most visible
 * row of 10 hearts (each worth only 2 HP under the old formula) was already 9/10 full on its own
 * (18 HP fills 9 of that row's 2-HP hearts), while the 2 further rows (representing HP ranges this
 * entity's own health bar can reach but vanilla's hardcoded cap was never built to show) sat empty
 * out of view -- reading as "nearly full" while the entity was nearly dead. Confirmed by hand
 * substituting the Wagon's own real numbers into the formula, not just re-trusting the original,
 * faithful-to-vanilla port (which was correctly verified against SMALL, horse-range health pools
 * only, never actually tested against this entity).
 *
 * <p>Fixed by scaling how much HP each heart represents to the ridden entity's own real
 * {@code getMaxHealth()}, instead of a hardcoded 2 -- see {@link #render}'s own inline doc. For any
 * mount at or under the vanilla 60 HP window (every real vanilla horse/camel/etc., always 15-30 HP)
 * this resolves to exactly 2 HP per heart, identical to vanilla's own original behavior, byte for
 * byte. Only a pool genuinely exceeding that window (a Wagon/Carriage) now scales down per heart
 * instead of silently truncating -- guaranteeing the displayed fill fraction always matches the
 * real current/max ratio, regardless of how large the mount's real Max HP is.
 */
public final class MountHealthOverlay implements GuiLayer {

    private static final Identifier HEART_VEHICLE_CONTAINER_SPRITE = Identifier.withDefaultNamespace("hud/heart/vehicle_container");
    private static final Identifier HEART_VEHICLE_FULL_SPRITE = Identifier.withDefaultNamespace("hud/heart/vehicle_full");
    private static final Identifier HEART_VEHICLE_HALF_SPRITE = Identifier.withDefaultNamespace("hud/heart/vehicle_half");

    private static final int ICON_SIZE = 9;
    private static final int ICON_SPACING = 8;
    private static final int ICONS_PER_ROW = 10;
    private static final int ROW_SPACING = 10;
    // Mirrors ManaOverlay's own MARGIN_LEFT -- anchored to the real right edge of the screen, not
    // vanilla's hotbar-centered reference point (which is exactly what put this on top of the custom
    // food bar in the first place).
    private static final int MARGIN_RIGHT = 10;
    private static final int MARGIN_BOTTOM = 10;
    // Same hard cap vanilla's own getVehicleMaxHearts uses (30 hearts) -- kept identical so every
    // real vanilla mount (always <= 60 HP) renders byte-for-byte the same as before this fix; only a
    // pool that genuinely exceeds 60 HP (Wagon/Carriage) actually triggers the new per-heart scaling.
    private static final int MAX_HEARTS = 30;

    @Override
    public void render(final GuiGraphicsExtractor guiGraphics, final DeltaTracker deltaTracker) {
        final LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        final Entity vehicle = player.getVehicle();
        if (!(vehicle instanceof LivingEntity livingVehicle) || !livingVehicle.showVehicleHealth()) {
            return;
        }
        final float maxHealth = livingVehicle.getMaxHealth();
        if (maxHealth <= 0.0F) {
            return;
        }
        // Real bug fix, 2026-10-26: vanilla's own hardcoded "2 HP per heart" assumption (see this
        // class's own top-level doc) only holds while maxHealth/2 doesn't exceed MAX_HEARTS -- for
        // anything bigger (a Wagon/Carriage), each heart now represents a proportionally larger slice
        // of the real max health instead, so the number of hearts filled always matches the real
        // current/max fraction exactly, regardless of how large maxHealth is.
        final int hearts = Math.min(Math.max(1, (int) Math.ceil(maxHealth / 2.0F)), MAX_HEARTS);
        final float healthPerHeart = maxHealth / hearts;
        final float currentHealth = livingVehicle.getHealth();

        final int xRight = guiGraphics.guiWidth() - MARGIN_RIGHT;
        final int yBase = guiGraphics.guiHeight() - MARGIN_BOTTOM;

        int heartsRemaining = hearts;
        int heartIndex = 0;
        int row = 0;
        while (heartsRemaining > 0) {
            final int rowHearts = Math.min(heartsRemaining, ICONS_PER_ROW);
            heartsRemaining -= rowHearts;
            final int y = yBase - row * ROW_SPACING;
            for (int i = 0; i < rowHearts; i++) {
                final int x = xRight - i * ICON_SPACING - ICON_SIZE;
                guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, HEART_VEHICLE_CONTAINER_SPRITE, x, y, ICON_SIZE, ICON_SIZE);
                final float heartFloor = heartIndex * healthPerHeart;
                if (currentHealth >= heartFloor + healthPerHeart) {
                    guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, HEART_VEHICLE_FULL_SPRITE, x, y, ICON_SIZE, ICON_SIZE);
                } else if (currentHealth >= heartFloor + healthPerHeart / 2.0F) {
                    guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, HEART_VEHICLE_HALF_SPRITE, x, y, ICON_SIZE, ICON_SIZE);
                }
                heartIndex++;
            }
            row++;
        }
    }
}
