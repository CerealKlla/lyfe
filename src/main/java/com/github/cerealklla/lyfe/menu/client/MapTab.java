package com.github.cerealklla.lyfe.menu.client;

import java.util.Optional;

import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multisets;
import com.mojang.blaze3d.platform.NativeImage;

import com.github.cerealklla.lyfe.api.Lyfe;
import com.github.cerealklla.lyfe.expeditionist.ExpeditionistConstants;
import com.github.cerealklla.lyfe.map.ClientMapState;
import com.github.cerealklla.lyfe.map.ClientQuestWaypointState;
import com.github.cerealklla.lyfe.map.ClientWaypointState;
import com.github.cerealklla.lyfe.map.MapSettlementsPayload;
import com.github.cerealklla.lyfe.map.MapZoom;
import com.github.cerealklla.lyfe.map.RequestMapDataPayload;
import com.github.cerealklla.lyfe.map.cache.MapPalette;
import com.github.cerealklla.lyfe.map.cache.TerrainCache;
import com.github.cerealklla.lyfe.skill.Skills;
import com.github.cerealklla.nuclyus.menu.client.NuclyusMenu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;

/**
 * "Map" tab (Expeditionist level 10 unlock) -- direct port of the former standalone {@code
 * map.client.MapScreen} into the shared menu, 2026-10-17 (see the root project's own CLAUDE.md for
 * the menu-consolidation feature). Behavior is otherwise unchanged from that class -- see its own
 * removed doc comment history for the full design rationale (terrain from the passively-built
 * {@code TerrainCache}, pan/zoom, settlement dots, custom/quest waypoints). The one real mechanical
 * difference from being a tab instead of a top-level {@code Screen}: every screen-absolute
 * coordinate is now derived from the injected {@code area} rect instead of this class's own
 * (nonexistent) {@code width}/{@code height} fields, and the former self-closing {@code keyPressed}
 * override is gone entirely -- that's host-level behavior now ({@code NuclyusMenuScreen} owns the
 * single K-closes-the-whole-menu check), not Map-specific.
 */
public final class MapTab implements NuclyusMenu {

    private static final int MARGIN = 20;
    private static final int BACKGROUND_COLOR = 0xF0000000;
    private static final int PLAYER_MARKER_COLOR = 0xFFFF5555;
    private static final int SETTLEMENT_DOT_COLOR = 0xFFFFFFFF;
    private static final int SETTLEMENT_DOT_RADIUS = 2;
    private static final int SETTLEMENT_HOVER_RADIUS = 5;
    private static final int WAYPOINT_DOT_COLOR = 0xFF5588FF;
    private static final int WAYPOINT_DOT_RADIUS = 3;
    private static final int QUEST_WAYPOINT_CIRCLE_COLOR = 0x40FF8800;
    private static final int QUEST_WAYPOINT_CIRCLE_BORDER_COLOR = 0xFFFF8800;
    private static final int WAYPOINT_CLICK_TOLERANCE = 8;

    private static final int MOVE_RESAMPLE_THRESHOLD_BLOCKS = 8;

    private static final int MIN_IMAGE_SIZE = 128;
    private static final int MAX_IMAGE_SIZE = ClientMapState.CANVAS_SIZE;

    private int zoomIndex = MapZoom.NO_ZOOM_INDEX;

    // Pan state -- null until the first drag; once set, the view no longer auto-follows the player
    // until this tab instance is discarded (the whole menu closing and reopening, same "forgets the
    // pan on close" spec the original standalone screen already had).
    private Double panCenterX;
    private Double panCenterZ;

    @Override
    public Component title() {
        return Component.literal("Map");
    }

    @Override
    public int order() {
        return 1;
    }

    @Override
    public boolean enabled() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && Lyfe.getLevel(player, Skills.EXPEDITIONIST_ID) >= ExpeditionistConstants.MAP_UNLOCK_LEVEL;
    }

    @Override
    public Component disabledReason() {
        return Component.literal("Requires Expeditionist level " + ExpeditionistConstants.MAP_UNLOCK_LEVEL);
    }

    @Override
    public void onShown() {
        var connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            connection.send(new ServerboundCustomPayloadPacket(new RequestMapDataPayload()));
        }
    }

    @Override
    public void onHidden() {
    }

    private boolean zoomUnlocked(LocalPlayer player) {
        return Lyfe.getLevel(player, Skills.EXPEDITIONIST_ID) >= ExpeditionistConstants.MAP_ZOOM_UNLOCK_LEVEL;
    }

    private boolean dotsUnlocked(LocalPlayer player) {
        return Lyfe.getLevel(player, Skills.EXPEDITIONIST_ID) >= ExpeditionistConstants.SETTLEMENT_DOTS_UNLOCK_LEVEL;
    }

    private boolean customWaypointUnlocked(LocalPlayer player) {
        return Lyfe.getLevel(player, Skills.EXPEDITIONIST_ID) >= ExpeditionistConstants.CUSTOM_WAYPOINT_UNLOCK_LEVEL;
    }

    private int viewportWidth(Rect2i area) {
        return area.getWidth() - 2 * MARGIN;
    }

    private int viewportHeight(Rect2i area) {
        return area.getHeight() - 2 * MARGIN;
    }

    private void maybeResample(ClientLevel level, LocalPlayer player, Rect2i area) {
        int viewportW = viewportWidth(area);
        int viewportH = viewportHeight(area);
        double aspect = viewportW / (double) viewportH;

        int radiusZ = Math.max(1, MapZoom.radiusForIndex(zoomIndex));
        int radiusX = Math.max(1, (int) Math.round(radiusZ * aspect));

        int targetImageSize = Math.max(MIN_IMAGE_SIZE, Math.min(MAX_IMAGE_SIZE, viewportH));
        int largerRadius = Math.max(radiusX, radiusZ);
        int scaleBlocks = Math.max(1, (int) Math.ceil((largerRadius * 2f) / targetImageSize));
        int imageWidth = Math.max(1, Math.min(ClientMapState.CANVAS_SIZE, Math.round((radiusX * 2f) / scaleBlocks)));
        int imageHeight = Math.max(1, Math.min(ClientMapState.CANVAS_SIZE, Math.round((radiusZ * 2f) / scaleBlocks)));

        int centerX = panCenterX != null ? (int) Math.round(panCenterX) : player.getBlockX();
        int centerZ = panCenterZ != null ? (int) Math.round(panCenterZ) : player.getBlockZ();
        boolean moved = Math.abs(centerX - ClientMapState.sampledCenterX()) > MOVE_RESAMPLE_THRESHOLD_BLOCKS
                || Math.abs(centerZ - ClientMapState.sampledCenterZ()) > MOVE_RESAMPLE_THRESHOLD_BLOCKS;
        boolean radiusChanged = radiusX != ClientMapState.sampledRadiusX() || radiusZ != ClientMapState.sampledRadiusZ();
        boolean imageSizeChanged = imageWidth != ClientMapState.sampledImageWidth() || imageHeight != ClientMapState.sampledImageHeight();

        if (!ClientMapState.hasTexture() || radiusChanged || imageSizeChanged || moved) {
            NativeImage image = buildImage(level, centerX, centerZ, radiusX, radiusZ, imageWidth, imageHeight, scaleBlocks);
            ClientMapState.setTexture(image, centerX, centerZ, radiusX, radiusZ, imageWidth, imageHeight);
        }
    }

    private static NativeImage buildImage(ClientLevel level, int centerX, int centerZ, int radiusX, int radiusZ,
            int imageWidth, int imageHeight, int scaleBlocks) {
        int canvasSize = ClientMapState.CANVAS_SIZE;
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, canvasSize, canvasSize, false);
        image.fillRect(0, 0, canvasSize, canvasSize, 0);
        var dimension = level.dimension();
        for (int col = 0; col < imageWidth; col++) {
            for (int row = 0; row < imageHeight; row++) {
                int areaMinX = centerX + (col - imageWidth / 2) * scaleBlocks;
                int areaMinZ = centerZ + (row - imageHeight / 2) * scaleBlocks;

                Multiset<Short> counts = LinkedHashMultiset.create();
                for (int dx = 0; dx < scaleBlocks; dx++) {
                    for (int dz = 0; dz < scaleBlocks; dz++) {
                        short value = TerrainCache.get(dimension, areaMinX + dx, areaMinZ + dz);
                        if (value != MapPalette.UNSAMPLED) {
                            counts.add(value);
                        }
                    }
                }
                if (counts.isEmpty()) {
                    continue;
                }
                short dominant = Iterables.getFirst(Multisets.copyHighestCountFirst(counts), MapPalette.UNSAMPLED);
                image.setPixelABGR(col, row, argbToAbgr(MapPalette.argb(dominant)));
            }
        }
        return image;
    }

    private static int argbToAbgr(int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        return (a << 24) | (b << 16) | (g << 8) | r;
    }

    private double pixelsPerBlockX(Rect2i area) {
        int radiusX = ClientMapState.sampledRadiusX();
        return radiusX <= 0 ? 0 : viewportWidth(area) / (double) (radiusX * 2);
    }

    private double pixelsPerBlockY(Rect2i area) {
        int radiusZ = ClientMapState.sampledRadiusZ();
        return radiusZ <= 0 ? 0 : viewportHeight(area) / (double) (radiusZ * 2);
    }

    @Override
    public boolean mouseScrolled(Rect2i area, double mouseX, double mouseY, double scrollX, double scrollY) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !zoomUnlocked(player)) {
            return false;
        }
        // Scroll-up (away from the player, positive scrollY) zooms in.
        zoomIndex = MapZoom.clampIndex(zoomIndex + (int) Math.signum(scrollY), true);
        return true;
    }

    @Override
    public boolean mouseClicked(Rect2i area, MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            // No other action on a plain left-click -- just capture the press so mouseDragged below
            // actually fires for it.
            return true;
        }
        if (event.button() == 1) {
            LocalPlayer player = Minecraft.getInstance().player;
            double ppbX = pixelsPerBlockX(area);
            double ppbY = pixelsPerBlockY(area);
            if (player != null && ppbX > 0 && ppbY > 0 && customWaypointUnlocked(player)) {
                int centerScreenX = area.getX() + area.getWidth() / 2;
                int centerScreenY = area.getY() + area.getHeight() / 2;
                int playerX = ClientMapState.sampledCenterX();
                int playerZ = ClientMapState.sampledCenterZ();

                boolean removedExisting = false;
                if (ClientWaypointState.isSet() && ClientWaypointState.dimension().equals(Optional.of(player.level().dimension()))) {
                    BlockPos existing = ClientWaypointState.pos().orElseThrow();
                    int existingScreenX = centerScreenX + (int) Math.round((existing.getX() - playerX) * ppbX);
                    int existingScreenY = centerScreenY + (int) Math.round((existing.getZ() - playerZ) * ppbY);
                    double distance = Math.hypot(event.x() - existingScreenX, event.y() - existingScreenY);
                    if (distance <= WAYPOINT_CLICK_TOLERANCE) {
                        ClientWaypointState.clear();
                        removedExisting = true;
                    }
                }
                if (!removedExisting) {
                    int worldX = playerX + (int) Math.round((event.x() - centerScreenX) / ppbX);
                    int worldZ = playerZ + (int) Math.round((event.y() - centerScreenY) / ppbY);
                    ClientWaypointState.set(new BlockPos(worldX, player.getBlockY(), worldZ), player.level().dimension());
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(Rect2i area, MouseButtonEvent event, double dragX, double dragY) {
        if (event.button() == 0) {
            double ppbX = pixelsPerBlockX(area);
            double ppbY = pixelsPerBlockY(area);
            if (ppbX > 0 && ppbY > 0) {
                if (panCenterX == null) {
                    panCenterX = (double) ClientMapState.sampledCenterX();
                    panCenterZ = (double) ClientMapState.sampledCenterZ();
                }
                panCenterX -= dragX / ppbX;
                panCenterZ -= dragY / ppbY;
            }
            return true;
        }
        return false;
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, Rect2i area, int mouseX, int mouseY, float partialTick) {
        graphics.fill(area.getX(), area.getY(), area.getX() + area.getWidth(), area.getY() + area.getHeight(), BACKGROUND_COLOR);

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null) {
            return;
        }

        maybeResample(level, player, area);

        int w = viewportWidth(area);
        int h = viewportHeight(area);
        int left = area.getX() + (area.getWidth() - w) / 2;
        int top = area.getY() + (area.getHeight() - h) / 2;
        int centerScreenX = left + w / 2;
        int centerScreenY = top + h / 2;

        graphics.fill(left - 1, top - 1, left + w + 1, top + h + 1, 0xFFFFFFFF);

        if (ClientMapState.hasTexture()) {
            int usedWidth = ClientMapState.sampledImageWidth();
            int usedHeight = ClientMapState.sampledImageHeight();
            int canvasSize = ClientMapState.CANVAS_SIZE;
            graphics.enableScissor(left, top, left + w, top + h);
            graphics.blit(RenderPipelines.GUI_TEXTURED, ClientMapState.textureId(), left, top, 0, 0,
                    w, h, usedWidth, usedHeight, canvasSize, canvasSize);
            graphics.disableScissor();
        }

        double ppbX = pixelsPerBlockX(area);
        double ppbY = pixelsPerBlockY(area);
        if (ppbX > 0 && ppbY > 0) {
            int playerX = ClientMapState.sampledCenterX();
            int playerZ = ClientMapState.sampledCenterZ();

            if (dotsUnlocked(player)) {
                for (MapSettlementsPayload.Entry entry : ClientMapState.settlements()) {
                    int x = centerScreenX + (int) Math.round((entry.x() - playerX) * ppbX);
                    int y = centerScreenY + (int) Math.round((entry.z() - playerZ) * ppbY);
                    if (x < left || x > left + w || y < top || y > top + h) {
                        continue;
                    }
                    graphics.fill(x - SETTLEMENT_DOT_RADIUS, y - SETTLEMENT_DOT_RADIUS,
                            x + SETTLEMENT_DOT_RADIUS, y + SETTLEMENT_DOT_RADIUS, SETTLEMENT_DOT_COLOR);
                    if (Math.hypot(mouseX - x, mouseY - y) <= SETTLEMENT_HOVER_RADIUS) {
                        graphics.setTooltipForNextFrame(minecraft.font, Component.literal(entry.displayText()), mouseX, mouseY);
                    }
                }
            }

            if (ClientWaypointState.isSet() && ClientWaypointState.dimension().equals(Optional.of(level.dimension()))
                    && customWaypointUnlocked(player)) {
                BlockPos waypoint = ClientWaypointState.pos().orElseThrow();
                int x = centerScreenX + (int) Math.round((waypoint.getX() - playerX) * ppbX);
                int y = centerScreenY + (int) Math.round((waypoint.getZ() - playerZ) * ppbY);
                if (x >= left && x <= left + w && y >= top && y <= top + h) {
                    graphics.fill(x - WAYPOINT_DOT_RADIUS, y - WAYPOINT_DOT_RADIUS,
                            x + WAYPOINT_DOT_RADIUS, y + WAYPOINT_DOT_RADIUS, WAYPOINT_DOT_COLOR);
                }
            }

            if (ClientQuestWaypointState.isSet() && ClientQuestWaypointState.dimension().equals(Optional.of(level.dimension()))) {
                BlockPos waypoint = ClientQuestWaypointState.pos().orElseThrow();
                int cxScreen = centerScreenX + (int) Math.round((waypoint.getX() - playerX) * ppbX);
                int czScreen = centerScreenY + (int) Math.round((waypoint.getZ() - playerZ) * ppbY);
                int screenRadiusX = (int) Math.round(ClientQuestWaypointState.radiusBlocks() * ppbX);
                int screenRadiusY = (int) Math.round(ClientQuestWaypointState.radiusBlocks() * ppbY);
                if (screenRadiusX > 0 && screenRadiusY > 0
                        && cxScreen + screenRadiusX >= left && cxScreen - screenRadiusX <= left + w
                        && czScreen + screenRadiusY >= top && czScreen - screenRadiusY <= top + h) {
                    graphics.enableScissor(left, top, left + w, top + h);
                    for (int row = -screenRadiusY; row <= screenRadiusY; row++) {
                        double frac = 1.0 - (row * (double) row) / (double) (screenRadiusY * screenRadiusY);
                        if (frac <= 0) {
                            continue;
                        }
                        int halfWidth = (int) Math.round(screenRadiusX * Math.sqrt(frac));
                        graphics.fill(cxScreen - halfWidth, czScreen + row, cxScreen + halfWidth, czScreen + row + 1, QUEST_WAYPOINT_CIRCLE_COLOR);
                    }
                    graphics.disableScissor();
                    graphics.fill(cxScreen - 2, czScreen - 2, cxScreen + 2, czScreen + 2, QUEST_WAYPOINT_CIRCLE_BORDER_COLOR);
                }
            }
        }

        if (ppbX > 0 && ppbY > 0) {
            int x = centerScreenX + (int) Math.round((player.getX() - ClientMapState.sampledCenterX()) * ppbX);
            int y = centerScreenY + (int) Math.round((player.getZ() - ClientMapState.sampledCenterZ()) * ppbY);
            if (x >= left && x <= left + w && y >= top && y <= top + h) {
                graphics.fill(x - 2, y - 2, x + 2, y + 2, PLAYER_MARKER_COLOR);
            }
        }
    }
}
