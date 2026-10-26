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
        // Same formula/cap as vanilla's own Gui#getVehicleMaxHearts -- (maxHealth + 0.5) / 2, capped
        // at 30 hearts (60 HP). A Wagon (100 HP)/Carriage (150 HP) both exceed this and simply render
        // a full 3/4-row grid, same as vanilla already does for anything else that large.
        int hearts = Math.min((int) (livingVehicle.getMaxHealth() + 0.5F) / 2, 30);
        if (hearts <= 0) {
            return;
        }
        final int currentHealth = (int) Math.ceil(livingVehicle.getHealth());

        final int xRight = guiGraphics.guiWidth() - MARGIN_RIGHT;
        final int yBase = guiGraphics.guiHeight() - MARGIN_BOTTOM;

        int baseHealth = 0;
        int row = 0;
        while (hearts > 0) {
            final int rowHearts = Math.min(hearts, ICONS_PER_ROW);
            hearts -= rowHearts;
            final int y = yBase - row * ROW_SPACING;
            for (int i = 0; i < rowHearts; i++) {
                final int x = xRight - i * ICON_SPACING - ICON_SIZE;
                guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, HEART_VEHICLE_CONTAINER_SPRITE, x, y, ICON_SIZE, ICON_SIZE);
                if (i * 2 + 1 + baseHealth < currentHealth) {
                    guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, HEART_VEHICLE_FULL_SPRITE, x, y, ICON_SIZE, ICON_SIZE);
                } else if (i * 2 + 1 + baseHealth == currentHealth) {
                    guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, HEART_VEHICLE_HALF_SPRITE, x, y, ICON_SIZE, ICON_SIZE);
                }
            }
            baseHealth += 20;
            row++;
        }
    }
}
