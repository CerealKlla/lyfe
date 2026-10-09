package com.github.cerealklla.lyfe.map.client;

import java.util.Optional;

import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multisets;
import com.mojang.blaze3d.platform.NativeImage;

import com.github.cerealklla.lyfe.LyfeModClient;
import com.github.cerealklla.lyfe.api.Lyfe;
import com.github.cerealklla.lyfe.expeditionist.ExpeditionistConstants;
import com.github.cerealklla.lyfe.map.ClientMapState;
import com.github.cerealklla.lyfe.map.ClientWaypointState;
import com.github.cerealklla.lyfe.map.MapSettlementsPayload;
import com.github.cerealklla.lyfe.map.MapZoom;
import com.github.cerealklla.lyfe.map.RequestMapDataPayload;
import com.github.cerealklla.lyfe.map.cache.MapPalette;
import com.github.cerealklla.lyfe.map.cache.TerrainCache;
import com.github.cerealklla.lyfe.skill.Skills;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;

/**
 * The full-screen Map (Expeditionist level 15, "press M") -- design doc's original spec, built as
 * its own slice after the Expeditionist skill/minimap unlock-gate pass, deliberately split off at
 * the time ("explicit scope split with the user," see {@code expeditionist.ExpeditionistConstants}'
 * own doc). Renders from {@code map.cache.TerrainCache} -- a persisted, palette-indexed grid the
 * client builds up passively as the player explores ({@code map.cache.TerrainCachePassiveSampler}),
 * per the user's own original spec ("store the bitmap... dynamically created and edited by the
 * player moving around the world"). An area never explored simply has no cached data and renders
 * transparent -- this directly satisfies "only show places you've been" rather than incidentally
 * matching it the way an always-live view would. Corrected 2026-10-07 after the Map was first built
 * live-sampling like the minimap (a wrong simplification the user caught and asked to be redone).
 *
 * <p>North-up fixed only -- no Auto-Rotate here, not asked for. Settlement dots with hover tooltips
 * unlock at level 20 ({@link ExpeditionistConstants#SETTLEMENT_DOTS_UNLOCK_LEVEL}), driven by a
 * one-shot {@link RequestMapDataPayload}/{@link MapSettlementsPayload} round trip on open (not a
 * continuous stream like the minimap's own outlines -- settlement knowledge doesn't change fast
 * enough to need it). Scroll-wheel zoom unlocks at level 25 ({@link MapZoom}).
 *
 * <p>Right-click places or removes the single active waypoint ({@link ClientWaypointState}) --
 * right-clicking near the existing waypoint's own screen position removes it, any other right-click
 * moves it there instead (spec: "only 1 waypoint active").
 *
 * <p>Left-click-drag pans the view ({@link #panCenterX}/{@link #panCenterZ}, world coordinates --
 * {@code null} means "follow the player live," set on the first drag). Deliberately a plain instance
 * field, not persisted anywhere -- the user's own explicit spec: "it forgets where the pan was when
 * closing, so opening the map again starts centered on the player again."
 */
public final class MapScreen extends Screen {

    private static final int MARGIN = 20;
    private static final int BACKGROUND_COLOR = 0xF0000000;
    private static final int PLAYER_MARKER_COLOR = 0xFFFF5555;
    private static final int SETTLEMENT_DOT_COLOR = 0xFFFFFFFF;
    private static final int SETTLEMENT_DOT_RADIUS = 2;
    private static final int SETTLEMENT_HOVER_RADIUS = 5;
    private static final int WAYPOINT_DOT_COLOR = 0xFF5588FF;
    private static final int WAYPOINT_DOT_RADIUS = 3;
    private static final int WAYPOINT_CLICK_TOLERANCE = 8;

    private static final int MOVE_RESAMPLE_THRESHOLD_BLOCKS = 8;

    // The *logical* (used-portion-of-the-canvas) resolution scales with the viewport's own pixel size
    // rather than a small fixed grid -- found live, 2026-10-07: a small fixed grid looked blocky once
    // stretched across a near-full-screen viewport. Clamped to ClientMapState.CANVAS_SIZE, the fixed
    // GPU texture's own real size (see that class's own doc for why the texture itself never resizes).
    private static final int MIN_IMAGE_SIZE = 128;
    private static final int MAX_IMAGE_SIZE = ClientMapState.CANVAS_SIZE;

    private int zoomIndex = MapZoom.NO_ZOOM_INDEX;

    // Pan state -- see this class's own doc. Null until the first drag; once set, the view no longer
    // auto-follows the player until this screen is closed and reopened.
    private Double panCenterX;
    private Double panCenterZ;

    public MapScreen() {
        super(Component.literal("Map"));
    }

    @Override
    protected void init() {
        var connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            connection.send(new ServerboundCustomPayloadPacket(new RequestMapDataPayload()));
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * Real root cause found live, 2026-10-08, of "pressing M a second time does nothing": confirmed
     * against the decompiled {@code KeyboardHandler#keyPress} source that {@code KeyMapping.click()}
     * (what {@code OPEN_MAP.consumeClick()} in {@code LyfeModClient}'s tick handler depends on) is
     * only ever invoked when {@code Minecraft.screen == null} -- while ANY {@code Screen} is open,
     * every keypress goes to that screen's own {@code keyPressed} first and the raw {@code
     * KeyMapping} click-counter below it in that method is never reached at all, by design (keybinds
     * are an in-game-only concept; a screen that wants to react to one has to check for it itself).
     * This is why toggling the Map *open* always worked (fired while no screen existed yet) but
     * toggling it *closed* from the same key never could, regardless of any fix on the
     * {@code LyfeModClient} side. Checking it directly here, inside this screen's own key handler,
     * is the actual fix.
     */
    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (LyfeModClient.OPEN_MAP.matches(event)) {
            Minecraft.getInstance().setScreen(null);
            return true;
        }
        return super.keyPressed(event);
    }

    private boolean zoomUnlocked(LocalPlayer player) {
        return Lyfe.getLevel(player, Skills.EXPEDITIONIST_ID) >= ExpeditionistConstants.MAP_ZOOM_UNLOCK_LEVEL;
    }

    private boolean dotsUnlocked(LocalPlayer player) {
        return Lyfe.getLevel(player, Skills.EXPEDITIONIST_ID) >= ExpeditionistConstants.SETTLEMENT_DOTS_UNLOCK_LEVEL;
    }

    // Fills the whole window (minus a small margin), not just a square bounded by the shorter
    // dimension -- user request, 2026-10-07 ("stretch proportionally to fit into the resolution")
    // after seeing a square panel leave large black bars on a widescreen window. A first attempt
    // stretched a square sample non-uniformly into this rectangle, which visibly distorted terrain
    // on a wide window ("slightly stretched horizontally", also live-reported) -- the real fix
    // samples a real-world area proportional to the viewport's own aspect ratio instead (more world
    // shown horizontally on a wide window, not the same square view stretched), keeping blocks-per-
    // pixel uniform on both axes. See ClientTerrainSampler's own per-axis overload.
    private int viewportWidth() {
        return width - 2 * MARGIN;
    }

    private int viewportHeight() {
        return height - 2 * MARGIN;
    }

    /**
     * Rebuilds the displayed texture from {@link TerrainCache} whenever the player has moved past the
     * threshold or the radius/image size changed -- no "sampling in flight" state needed anymore
     * (see this class's own doc): a cache read is plain in-memory array lookups, cheap enough to run
     * synchronously on the render thread, unlike the old live-sampling version this replaced.
     *
     * <p>No render-distance clamp here (unlike {@code minimap.MinimapOverlay#maybeResample}, which
     * still needs one) -- that clamp existed only to avoid sampling past what the client's own loaded
     * chunks could guarantee; {@link TerrainCache} has no such dependency at all (it's reading
     * previously-recorded data, not the live world), so an area outside render distance -- or even
     * outside the current session's loaded chunks entirely -- renders exactly like any other
     * unexplored area: transparent, not torn.
     */
    private void maybeResample(ClientLevel level, LocalPlayer player) {
        int viewportW = viewportWidth();
        int viewportH = viewportHeight();
        double aspect = viewportW / (double) viewportH;

        // radiusZ is the "nominal" zoom radius (vertical half-extent); radiusX is derived from the
        // viewport's own aspect ratio so a single blocks-per-pixel scale applies to both axes.
        int radiusZ = Math.max(1, MapZoom.radiusForIndex(zoomIndex));
        int radiusX = Math.max(1, (int) Math.round(radiusZ * aspect));

        // Image dimensions are derived from a single shared scaleBlocks (real blocks per sampled
        // pixel) rather than independently fitting width/height to the viewport -- rounding each
        // axis's scale to the nearest integer *separately* can land on genuinely different small
        // integers even when width/height were already aspect-matched to radiusX/radiusZ, a real bug
        // found live during the old live-sampling version. Deriving width/height *from* one shared
        // scaleBlocks guarantees both axes land on the exact same integer, by construction.
        // scaleBlocks must be derived from whichever axis (X or Z) would produce the LARGER image --
        // real crash found live, 2026-10-08: it was previously derived from radiusZ/targetImageHeight
        // alone, but radiusX is scaled up by the viewport's own aspect ratio (always >= radiusZ on a
        // widescreen display), so imageWidth could exceed CANVAS_SIZE while imageHeight stayed safely
        // within it -- NativeImage#setPixelABGR then threw "outside of image bounds" the moment a
        // sampled column landed past the real 512px canvas. Math.ceil (not round) guarantees both
        // axes' rounded pixel counts stay <= the target, never 1px over from rounding up.
        int targetImageSize = Math.max(MIN_IMAGE_SIZE, Math.min(MAX_IMAGE_SIZE, viewportH));
        int largerRadius = Math.max(radiusX, radiusZ);
        int scaleBlocks = Math.max(1, (int) Math.ceil((largerRadius * 2f) / targetImageSize));
        int imageWidth = Math.max(1, Math.min(ClientMapState.CANVAS_SIZE, Math.round((radiusX * 2f) / scaleBlocks)));
        int imageHeight = Math.max(1, Math.min(ClientMapState.CANVAS_SIZE, Math.round((radiusZ * 2f) / scaleBlocks)));

        // The sampled view center follows the player unless panned (see this class's own doc) -- once
        // panned, player movement alone no longer triggers a resample, only an actual further pan or
        // a radius/zoom change does.
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

    /**
     * Each output pixel aggregates the {@code scaleBlocks x scaleBlocks} columns it covers by
     * majority-vote over {@link TerrainCache} reads (same dominant-value idea {@code
     * ClientTerrainSampler} already used for live sampling, just over cached shorts instead of fresh
     * block-state queries) -- a cell with no sampled columns at all stays fully transparent.
     *
     * <p>Always allocates the full fixed {@link ClientMapState#CANVAS_SIZE} canvas (even though only
     * the {@code imageWidth x imageHeight} top-left corner is actually drawn into) -- see {@link
     * ClientMapState}'s own doc for why the underlying GPU texture must never change size.
     */
    private static NativeImage buildImage(ClientLevel level, int centerX, int centerZ, int radiusX, int radiusZ,
            int imageWidth, int imageHeight, int scaleBlocks) {
        int canvasSize = ClientMapState.CANVAS_SIZE;
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, canvasSize, canvasSize, false);
        image.fillRect(0, 0, canvasSize, canvasSize, 0); // Fully transparent -- stale content from a smaller previous frame must not linger at the edges.
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
                    continue; // Never explored -- leave fully transparent.
                }
                short dominant = Iterables.getFirst(Multisets.copyHighestCountFirst(counts), MapPalette.UNSAMPLED);
                image.setPixelABGR(col, row, argbToAbgr(MapPalette.argb(dominant)));
            }
        }
        return image;
    }

    /** {@link NativeImage#setPixelABGR} expects byte order opposite normal ARGB -- same channel swap {@code ClientTerrainSampler} already needs for the same reason. */
    private static int argbToAbgr(int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        return (a << 24) | (b << 16) | (g << 8) | r;
    }

    private double pixelsPerBlockX() {
        int radiusX = ClientMapState.sampledRadiusX();
        return radiusX <= 0 ? 0 : viewportWidth() / (double) (radiusX * 2);
    }

    private double pixelsPerBlockY() {
        int radiusZ = ClientMapState.sampledRadiusZ();
        return radiusZ <= 0 ? 0 : viewportHeight() / (double) (radiusZ * 2);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !zoomUnlocked(player)) {
            return false;
        }
        // Scroll-up (away from the player, positive scrollY) zooms in -- user report, 2026-10-07: the
        // first version had this inverted.
        zoomIndex = MapZoom.clampIndex(zoomIndex + (int) Math.signum(scrollY), true);
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            // No other action on a plain left-click -- just capture the press so mouseDragged below
            // actually fires for it (same convention vanilla's own draggable widgets, e.g. sliders,
            // rely on).
            return true;
        }
        if (event.button() == 1) {
            LocalPlayer player = Minecraft.getInstance().player;
            double ppbX = pixelsPerBlockX();
            double ppbY = pixelsPerBlockY();
            if (player != null && ppbX > 0 && ppbY > 0) {
                int centerScreenX = width / 2;
                int centerScreenY = height / 2;
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
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragZ) {
        if (event.button() == 0) {
            double ppbX = pixelsPerBlockX();
            double ppbY = pixelsPerBlockY();
            if (ppbX > 0 && ppbY > 0) {
                if (panCenterX == null) {
                    panCenterX = (double) ClientMapState.sampledCenterX();
                    panCenterZ = (double) ClientMapState.sampledCenterZ();
                }
                // Dragging right/down should reveal world content that was off to the right/below --
                // i.e. the view center moves the opposite direction from the drag.
                panCenterX -= dragX / ppbX;
                panCenterZ -= dragZ / ppbY;
            }
            return true;
        }
        return super.mouseDragged(event, dragX, dragZ);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, BACKGROUND_COLOR);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null) {
            return;
        }

        maybeResample(level, player);

        int w = viewportWidth();
        int h = viewportHeight();
        int left = (width - w) / 2;
        int top = (height - h) / 2;
        int centerScreenX = left + w / 2;
        int centerScreenY = top + h / 2;

        graphics.fill(left - 1, top - 1, left + w + 1, top + h + 1, 0xFFFFFFFF);

        if (ClientMapState.hasTexture()) {
            // srcWidth/srcHeight = only the logically-used top-left corner of the canvas; texWidth/
            // texHeight = the real, fixed, never-resized GPU texture size (see ClientMapState's own
            // doc) -- these are deliberately different now, not the same value repeated.
            int usedWidth = ClientMapState.sampledImageWidth();
            int usedHeight = ClientMapState.sampledImageHeight();
            int canvasSize = ClientMapState.CANVAS_SIZE;
            graphics.enableScissor(left, top, left + w, top + h);
            graphics.blit(RenderPipelines.GUI_TEXTURED, ClientMapState.textureId(), left, top, 0, 0,
                    w, h, usedWidth, usedHeight, canvasSize, canvasSize);
            graphics.disableScissor();
        }

        double ppbX = pixelsPerBlockX();
        double ppbY = pixelsPerBlockY();
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
                        graphics.setTooltipForNextFrame(font, Component.literal(entry.displayText()), mouseX, mouseY);
                    }
                }
            }

            if (ClientWaypointState.isSet() && ClientWaypointState.dimension().equals(Optional.of(level.dimension()))) {
                BlockPos waypoint = ClientWaypointState.pos().orElseThrow();
                int x = centerScreenX + (int) Math.round((waypoint.getX() - playerX) * ppbX);
                int y = centerScreenY + (int) Math.round((waypoint.getZ() - playerZ) * ppbY);
                if (x >= left && x <= left + w && y >= top && y <= top + h) {
                    graphics.fill(x - WAYPOINT_DOT_RADIUS, y - WAYPOINT_DOT_RADIUS,
                            x + WAYPOINT_DOT_RADIUS, y + WAYPOINT_DOT_RADIUS, WAYPOINT_DOT_COLOR);
                }
            }
        }

        // The player marker is drawn at the player's own real screen position, not always dead center
        // -- once panned, the view is no longer centered on the player at all.
        if (ppbX > 0 && ppbY > 0) {
            int x = centerScreenX + (int) Math.round((player.getX() - ClientMapState.sampledCenterX()) * ppbX);
            int y = centerScreenY + (int) Math.round((player.getZ() - ClientMapState.sampledCenterZ()) * ppbY);
            if (x >= left && x <= left + w && y >= top && y <= top + h) {
                graphics.fill(x - 2, y - 2, x + 2, y + 2, PLAYER_MARKER_COLOR);
            }
        }
    }
}
