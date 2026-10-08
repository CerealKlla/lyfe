package com.github.cerealklla.lyfe.minimap;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.github.cerealklla.lyfe.api.Lyfe;
import com.github.cerealklla.lyfe.expeditionist.ExpeditionistConstants;
import com.github.cerealklla.lyfe.map.ClientWaypointState;
import com.github.cerealklla.lyfe.skill.Skills;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.client.gui.GuiLayer;

/**
 * Persistent top-right minimap -- real terrain via asynchronous client-side sampling (see {@link
 * ClientTerrainSampler}), Fixed (north-up) or Auto-Rotate orientation -- **Auto-Rotate is the
 * default as of 2026-09-30**, per explicit request (toggleable either way via the {@code
 * key.lyfe.minimap_toggle_rotation} keybind, see {@link ClientMinimapState#autoRotate()}), a fixed
 * player marker at dead center, and thin outlines
 * for nearby known Settlemynts plots/settlements ({@link MinimapEntitiesPayload}, via {@link
 * ClientMinimapState}). {@code location.LocationOverlay} anchors directly beneath this one -- see
 * {@link #RESERVED_HEIGHT}.
 */
public final class MinimapOverlay implements GuiLayer {

    public static final int SIZE = 100;
    private static final int MARGIN = 6;
    private static final int BORDER_COLOR = 0xFFFFFFFF;
    private static final int BORDER_THICKNESS = 2;
    private static final int BACKGROUND_COLOR = 0xE0101010;
    private static final int PLAYER_MARKER_COLOR = 0xFFFF5555;

    /** Total vertical space this overlay occupies, including its own margin -- {@code location.LocationOverlay} anchors below this. */
    public static final int RESERVED_HEIGHT = SIZE + MARGIN * 2;

    // Lowered from 8/20 to 4/10 to 4/2 to 4/1, user request 2026-09-29 ("increase the refresh rate
    // of the minimap" -> "2 ticks" -> "let's try 1"). Safe to push this low because sampling is
    // entirely async and self-throttling -- maybeResample bails out immediately whenever
    // ClientMinimapState.isSampling() is already true (checked before the interval counter even
    // increments), so this interval can never cause more than one sample in flight regardless of its
    // value -- at 1 it just means a fresh sample starts the instant the previous one finishes, as
    // fast as Util.backgroundExecutor() (a shared background thread pool, not the render/client
    // thread) can go, trading a bit of continuous background CPU for max responsiveness.
    private static final int MOVE_RESAMPLE_THRESHOLD_BLOCKS = 4;
    private static final int RESAMPLE_INTERVAL_TICKS = 1;

    // Not-yet-finalized stakes -- see ClientGhostMarkerOutlines' own doc for why these need a
    // separate, purely client-side path instead of MinimapEntitiesPayload (they're never registered
    // with Cartographyr until Finalize succeeds).
    private static final Identifier GHOST_PLOT_STAKE_ID = Identifier.fromNamespaceAndPath("settlemynts", "ghost_plot_stake");
    private static final Identifier GHOST_PERIMETER_STAKE_ID = Identifier.fromNamespaceAndPath("settlemynts", "ghost_perimeter_stake");
    private static final int IN_PROGRESS_STAKE_COLOR = 0xFFFFDD44;
    private static final int MARKER_DOT_RADIUS = 2;

    // Building Locator preview markers (Blueprynts' construction.GhostBuildingPreviewEntity) --
    // color read directly off each marker's own floating block, not a fixed constant, so this stays
    // correct even if the mapping on that side changes; see ClientGhostMarkerOutlines' own doc for
    // why no compiled dependency is needed to do this.
    private static final Identifier GHOST_BUILDING_PREVIEW_ID = Identifier.fromNamespaceAndPath("blueprynts", "ghost_building_preview");
    private static final int BUILDING_ZONE_COLOR = 0xFFFFEE55;
    private static final int BUILDING_FIT_COLOR = 0xFF55FF55;
    private static final int BUILDING_NO_FIT_COLOR = 0xFFFF5555;

    // North arrow -- gated behind Expeditionist level 10 (ExpeditionistConstants.
    // NORTH_INDICATOR_UNLOCK_LEVEL), added 2026-10-07 once the Expeditionist skill existed to gate it
    // with, per the user's own original design for this arrow ("this arrow can be level blocked by a
    // skill").
    private static final int NORTH_ARROW_COLOR = 0xFFFFFFFF;
    private static final int NORTH_ARROW_HALF_WIDTH = 4;
    private static final int NORTH_ARROW_HEIGHT = 6;
    // Gap between the arrow's apex and the circle's true edge, clearing the BORDER_THICKNESS ring
    // entirely -- the original placement put the arrow *outside* radius, which also meant outside the
    // outer render()-level enableScissor square (left/top/right/bottom, which only touches the circle
    // exactly at the 4 cardinal points): fine at diagonal rotation angles where the square has slack
    // past the circle, but clipped flat at cardinal-ish angles where the square and circle coincide
    // (user report, 2026-10-07, confirmed live). Moving the whole arrow inside the circle's own
    // perimeter avoids the square entirely, not just the border ring.
    private static final int NORTH_ARROW_INSET = BORDER_THICKNESS + 2;

    // Waypoint arrow -- 2026-10-07, the Map screen/waypoint pass. 90% the North arrow's own size per
    // spec ("so that if they overlap both are meaningfully visible"), own color, drawn after (so on
    // top of) the North arrow for the same reason. No Expeditionist level gate here directly -- it
    // only ever appears once a waypoint actually exists, which itself requires the Map screen (level
    // 15) to place one.
    private static final int WAYPOINT_ARROW_COLOR = 0xFF5588FF;
    private static final int WAYPOINT_ARROW_HALF_WIDTH = (int) Math.round(NORTH_ARROW_HALF_WIDTH * 0.9);
    private static final int WAYPOINT_ARROW_HEIGHT = (int) Math.round(NORTH_ARROW_HEIGHT * 0.9);

    private int ticksSinceLastSample = RESAMPLE_INTERVAL_TICKS;

    @Override
    public void render(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null) {
            return;
        }
        // Expeditionist gate (2026-10-07 user request): the minimap doesn't exist at all below this
        // level -- not a greyed-out/disabled state, nothing renders.
        if (Lyfe.getLevel(player, Skills.EXPEDITIONIST_ID) < ExpeditionistConstants.MINIMAP_UNLOCK_LEVEL) {
            return;
        }

        maybeResample(level, player);

        int right = guiGraphics.guiWidth() - MARGIN;
        int top = MARGIN;
        int left = right - SIZE;
        int bottom = top + SIZE;
        int radius = SIZE / 2;
        int cx = left + radius;
        int cz = top + radius;

        // Genuinely circular now, not a square masked to look circular (user report, 2026-09-29,
        // after the masking version: "there are still black remnants of the UI behind the circle...
        // make that fully transparent"). A `fill()` with alpha 0 can't erase pixels another draw call
        // already wrote -- it just blends nothing on top, leaving whatever's there -- so the only way
        // to get real transparency in the corners is to never draw into them at all. Fixed via a real
        // per-row scissor clip (see drawTerrainTexture below) instead of a full-square background
        // fill; the circle's own interior backdrop is filled per-row here for the same reason (so it
        // still reads as a solid panel before the first terrain sample arrives, or through gaps in a
        // translucent texture) without ever touching the square's actual corners.
        guiGraphics.enableScissor(left, top, right, bottom);
        for (int row = 0; row < SIZE; row++) {
            int half = circleHalfWidthAt(radius, row - radius);
            if (half > 0) {
                guiGraphics.fill(cx - half, top + row, cx + half, top + row + 1, BACKGROUND_COLOR);
            }
        }

        // Auto-Rotate, wired up 2026-09-29 for a live test (see ClientMinimapState's own doc) --
        // everything except the player marker is rotated about the minimap's own center so "up" on
        // screen always matches the player's current facing, north-up's usual "up = north" only
        // recovered when facing north (yaw 180). Derivation: the default (unrotated) screen vector
        // for the player's own facing direction is (-sin(yaw), cos(yaw)) in this map's existing
        // (east=+x/right, south=+z/down) convention; solving for the rotation that sends that vector
        // to (0,-1) ("up") gives angle = 180 - yaw, applied directly since JOML's rotation matrix
        // convention matches this same unmodified (x, y-down) formula.
        boolean autoRotate = ClientMinimapState.autoRotate();
        float rotationRadians = autoRotate ? (float) Math.toRadians(180.0 - player.getYRot()) : 0f;

        // Terrain gets its own per-row scissor+rotate+blit+unrotate+unscissor cycle (see
        // drawTerrainTexture's own doc for why the scissor for each row must be set *before* rotation
        // is pushed, scoped tightly around just that row's blit -- a real, intermittent bug found
        // live, 2026-09-29 ("sometimes it renders the terrain and sometimes it doesn't... look how
        // goofy this looks", a faceted non-circular shape): the earlier version pushed rotation once
        // for the whole content block and then issued each row's scissor *while already rotated*,
        // which is wrong -- a scissor rect is meant to be given in absolute screen space, not the
        // current (possibly rotated) local space.
        drawTerrainTexture(guiGraphics, left, top, autoRotate, rotationRadians, cx, cz);

        // Outlines/stakes/the Building Locator preview don't need per-row scissoring at all -- a
        // single rotation block around all three (exactly like the terrain conceptually wants, but
        // scissor-free so there's no ordering trap to get wrong), combined with a plain
        // distance-from-center check before each individual pixel/dot fill (added to drawLine and
        // the two dot-fill call sites below) for the circular clipping itself. That check is valid
        // regardless of whether rotation is currently active: rotation about (cx, cz) never changes a
        // point's distance from (cx, cz), so testing the pre-rotation coordinates is exactly
        // equivalent to testing where the point ends up after rotation.
        if (autoRotate) {
            guiGraphics.pose().pushMatrix();
            guiGraphics.pose().rotateAbout(rotationRadians, cx, cz);
        }
        // Shrunk by MARKER_DOT_RADIUS, not just BORDER_THICKNESS -- a real gap found live, 2026-09-29
        // ("lines still extend maybe a few pixels outside the circle"): isWithinPanel only tests a
        // dot's own center point, so a dot centered right at the panel's edge still had its square
        // extent (+-MARKER_DOT_RADIUS) poking past it. Lines get the same, slightly more generous
        // margin for free -- harmless, they just stop a couple pixels earlier.
        int contentRadius = radius - BORDER_THICKNESS - MARKER_DOT_RADIUS;
        drawOutlines(guiGraphics, left, top, cx, cz, contentRadius);
        drawInProgressStakes(guiGraphics, left, top, level, player, cx, cz, contentRadius);
        drawBuildingLocatorPreview(guiGraphics, left, top, level, player, cx, cz, contentRadius);
        // Drawn inside this same (conditionally rotated) block, not after it -- when Auto-Rotate is
        // on, the pose is rotated so "up" matches the player's facing, and north is wherever that
        // rotation now puts it; drawing the arrow at its default "straight up" position *inside* the
        // rotation swings it around the perimeter to the correct real-world-north angle for free, the
        // same trick drawOutlines/the stakes/the Building Locator preview already rely on. When
        // Auto-Rotate is off, this block never pushes a rotation at all, so the arrow just stays at
        // the top -- correct, since Fixed orientation is already north-up. Separate, higher
        // Expeditionist gate than the minimap's own (level 10 vs. 5) -- the minimap can be unlocked
        // without the North indicator yet.
        if (Lyfe.getLevel(player, Skills.EXPEDITIONIST_ID) >= ExpeditionistConstants.NORTH_INDICATOR_UNLOCK_LEVEL) {
            drawBearingArrow(guiGraphics, cx, cz, radius, 0f, NORTH_ARROW_HALF_WIDTH, NORTH_ARROW_HEIGHT, NORTH_ARROW_COLOR);
        }
        // Waypoint arrow, drawn after (on top of) the North arrow -- see WAYPOINT_ARROW_* doc.
        // Dimension-gated: a waypoint set in a different dimension than the player's current one is
        // never drawn (see ClientWaypointState's own doc for why it's cleared outright on a
        // dimension change rather than left stale).
        if (ClientWaypointState.isSet() && ClientWaypointState.dimension().equals(Optional.of(level.dimension()))) {
            BlockPos waypoint = ClientWaypointState.pos().orElseThrow();
            double dx = waypoint.getX() + 0.5 - player.getX();
            double dz = waypoint.getZ() + 0.5 - player.getZ();
            float bearingRadians = (float) Math.atan2(dx, -dz);
            drawBearingArrow(guiGraphics, cx, cz, radius, bearingRadians, WAYPOINT_ARROW_HALF_WIDTH, WAYPOINT_ARROW_HEIGHT, WAYPOINT_ARROW_COLOR);
        }
        if (autoRotate) {
            guiGraphics.pose().popMatrix();
        }

        drawCircularBorder(guiGraphics, left, top);

        // Always dead center, never itself rotated -- a plain symmetric square, so this is drawn
        // last/outside the rotation block either way, but kept explicit for clarity.
        guiGraphics.fill(cx - 2, cz - 2, cx + 2, cz + 2, PLAYER_MARKER_COLOR);
        guiGraphics.disableScissor();
    }

    /**
     * The terrain texture, clipped to the inscribed circle (radius {@code SIZE/2}) via a real
     * per-row scissor rather than drawn as a full square and painted over afterward -- the latter
     * can only ever hide content behind an opaque fill, never actually show the game world through
     * it (see this class's own doc, 2026-09-29). Re-issues the same full blit once per row, each
     * time scissored down to just that row's circular width; cheap for a 100x100-ish overlay (a
     * bounded, small number of extra draw calls, not a per-pixel cost).
     *
     * <p><b>Scissor must be set before rotation is pushed, every time</b> -- a real, intermittent bug
     * found live, 2026-09-29: an earlier version pushed the rotation once for the whole content block
     * and issued each row's scissor from inside it, which is wrong. A scissor rect is specified in
     * absolute screen space; giving it while the pose is already rotated effectively asks for "this
     * absolute rect, but then transform it by the current rotation too" the next time anything is
     * drawn, producing a corrupted, rotation-dependent (and here, empty-most-of-the-time) clip region.
     * Each row now pushes rotation fresh, scoped tightly around just that row's own blit call, with
     * the scissor already active and fixed in absolute space before rotation ever begins.
     */
    private void drawTerrainTexture(GuiGraphicsExtractor guiGraphics, int left, int top, boolean autoRotate, float rotationRadians, float cx, float cz) {
        if (!ClientMinimapState.hasTexture()) {
            return;
        }
        int radius = SIZE / 2;
        int cxInt = left + radius;
        for (int row = 0; row < SIZE; row++) {
            int half = circleHalfWidthAt(radius, row - radius);
            if (half <= 0) {
                continue;
            }
            int y = top + row;
            // enableScissor/disableScissor is a real stack (confirmed via decompiled source after a
            // separate real bug, 2026-09-29: calling enableScissor repeatedly without a disableScissor
            // between each call left ~SIZE unpopped entries active for the rest of the frame and
            // beyond, corrupting every screen rendered afterward -- including the pause menu, which is
            // why Escape produced a click sound but no visible menu at all). Each row must push and
            // pop its own scissor in turn, never leaving more than one extra level on the stack.
            guiGraphics.enableScissor(cxInt - half, y, cxInt + half, y + 1);
            if (autoRotate) {
                guiGraphics.pose().pushMatrix();
                guiGraphics.pose().rotateAbout(rotationRadians, cx, cz);
            }
            guiGraphics.blit(RenderPipelines.GUI_TEXTURED, ClientMinimapState.textureId(), left, top, 0, 0,
                    SIZE, SIZE, ClientTerrainSampler.IMAGE_SIZE, ClientTerrainSampler.IMAGE_SIZE,
                    ClientTerrainSampler.IMAGE_SIZE, ClientTerrainSampler.IMAGE_SIZE);
            if (autoRotate) {
                guiGraphics.pose().popMatrix();
            }
            guiGraphics.disableScissor();
        }
        // No explicit "restore" needed -- popping every row's own scissor above already returns to
        // whichever scissor was active before this method was called (render()'s own outer square).
    }

    /** A {@link #BORDER_THICKNESS}-pixel ring drawn just inside the circle's own edge -- one pair of horizontal strips per row, replacing the old 4-rectangle square frame. */
    private void drawCircularBorder(GuiGraphicsExtractor guiGraphics, int left, int top) {
        int radius = SIZE / 2;
        int cx = left + radius;
        for (int row = 0; row < SIZE; row++) {
            int y = top + row;
            int dz = row - radius;
            int outerHalf = circleHalfWidthAt(radius, dz);
            int innerHalf = circleHalfWidthAt(radius - BORDER_THICKNESS, dz);
            int circleLeft = cx - outerHalf;
            int circleRight = cx + outerHalf;
            int bandInnerLeft = cx - innerHalf;
            int bandInnerRight = cx + innerHalf;
            guiGraphics.fill(circleLeft, y, bandInnerLeft, y + 1, BORDER_COLOR);
            guiGraphics.fill(bandInnerRight, y, circleRight, y + 1, BORDER_COLOR);
        }
    }

    /**
     * A small solid triangle pointing outward (apex nearest the edge, base toward center), sitting
     * just inside the circle's own border ring -- not outside the circle, which used to clip at
     * certain rotation angles (see {@link #NORTH_ARROW_INSET}'s own doc). {@code bearingRadians}
     * places it anywhere around the perimeter (0 = straight up/north, increasing clockwise, matching
     * real-world bearing) -- generalized 2026-10-07 from the North-only version so the same method
     * also draws the waypoint arrow at its own real bearing. At a non-north bearing the triangle
     * itself must also rotate (apex-to-center is no longer simply "downward" in screen space), so
     * this walks along the apex->center direction and its perpendicular directly, filling one pixel
     * at a time -- cheap at this size (a handful of pixels), and the only correct option since no
     * triangle-fill primitive exists on {@code GuiGraphicsExtractor} and {@code fill}'s rectangles
     * can't express an arbitrary rotation the way the old axis-aligned, north-only version got away
     * with via per-row rectangles.
     */
    private void drawBearingArrow(GuiGraphicsExtractor guiGraphics, int cx, int cz, int radius,
            float bearingRadians, int halfWidth, int height, int color) {
        int inset = radius - NORTH_ARROW_INSET;
        float apexX = cx + inset * (float) Math.sin(bearingRadians);
        float apexY = cz - inset * (float) Math.cos(bearingRadians);
        // Unit vector from the apex toward the center (opposite of the outward bearing direction),
        // and its perpendicular for the triangle's width.
        float alongX = -(float) Math.sin(bearingRadians);
        float alongY = (float) Math.cos(bearingRadians);
        float perpX = alongY;
        float perpY = -alongX;
        for (int row = 0; row < height; row++) {
            float rowHalfWidth = (float) (halfWidth * (row / (double) (height - 1)));
            int steps = Math.max(1, Math.round(rowHalfWidth * 2) + 1);
            for (int i = 0; i < steps; i++) {
                float offset = steps == 1 ? 0f : -rowHalfWidth + (2 * rowHalfWidth) * i / (steps - 1);
                int x = Math.round(apexX + alongX * row + perpX * offset);
                int y = Math.round(apexY + alongY * row + perpY * offset);
                guiGraphics.fill(x, y, x + 1, y + 1, color);
            }
        }
    }

    /** Half-width (in pixels) of a circle of the given {@code radius} at vertical offset {@code dz} from its center -- 0 once {@code dz} is outside the circle entirely. */
    private static int circleHalfWidthAt(int radius, int dz) {
        if (Math.abs(dz) > radius) {
            return 0;
        }
        return (int) Math.floor(Math.sqrt((double) radius * radius - (double) dz * dz));
    }

    /** Throttled: a fresh sample only kicks off once the player has moved past a threshold, the radius changed, or on the periodic cadence -- and never while one is already in flight. */
    private void maybeResample(ClientLevel level, LocalPlayer player) {
        if (ClientMinimapState.isSampling()) {
            return;
        }
        ticksSinceLastSample++;

        int expeditionistLevel = Lyfe.getLevel(player, Skills.EXPEDITIONIST_ID);
        int uncappedRadius = MinimapZoom.radiusFor(expeditionistLevel);
        // Never sample further than the client can actually guarantee is loaded -- real bug found
        // live, 2026-10-07 ("tearing" lines, worst at full zoom-out, gone once zoomed in a couple of
        // steps, happening regardless of where/how the player was moving): ClientTerrainSampler
        // leaves a transparent gap for any not-yet-loaded chunk, and the minimap's own radius was
        // free to exceed the client's actual render distance, so the outer ring of the sampled image
        // sat right at (or past) the edge of loaded terrain -- which flickers in and out of being
        // loaded as the chunk grid shifts with normal movement, regardless of whether the area is
        // newly- or long-since-explored. getEffectiveRenderDistance() already accounts for a
        // server-enforced view-distance cap, not just the client's own setting. One chunk of margin
        // so the outermost sampled ring isn't sitting exactly on the boundary chunk, which churns
        // load/unload the most.
        int renderDistanceBlocks = Math.max(1, Minecraft.getInstance().options.getEffectiveRenderDistance() - 1) * 16;
        int radius = Math.min(uncappedRadius, renderDistanceBlocks);

        int playerX = player.getBlockX();
        int playerZ = player.getBlockZ();
        boolean moved = Math.abs(playerX - ClientMinimapState.sampledCenterX()) > MOVE_RESAMPLE_THRESHOLD_BLOCKS
                || Math.abs(playerZ - ClientMinimapState.sampledCenterZ()) > MOVE_RESAMPLE_THRESHOLD_BLOCKS;
        boolean radiusChanged = radius != ClientMinimapState.sampledRadius();
        boolean due = ticksSinceLastSample >= RESAMPLE_INTERVAL_TICKS;

        if (!ClientMinimapState.hasTexture() || radiusChanged || (due && moved)) {
            ticksSinceLastSample = 0;
            ClientMinimapState.beginSampling();
            ClientTerrainSampler.sampleAsync(level, playerX, playerZ, radius,
                    image -> ClientMinimapState.setTexture(image, playerX, playerZ, radius));
        }
    }

    private void drawOutlines(GuiGraphicsExtractor guiGraphics, int left, int top, float cx, float cz, int panelRadius) {
        int radius = ClientMinimapState.sampledRadius();
        if (radius <= 0) {
            return;
        }
        double pixelsPerBlock = (double) SIZE / (radius * 2);
        for (MinimapEntitiesPayload.Outline outline : ClientMinimapState.outlines()) {
            int color = outline.settlement() ? 0xFFFFFFFF : 0xFF55AAFF;
            var xs = outline.relativeX();
            var zs = outline.relativeZ();
            int count = xs.size();
            for (int i = 0; i < count; i++) {
                int j = (i + 1) % count;
                int x1 = left + SIZE / 2 + (int) Math.round(xs.get(i) * pixelsPerBlock);
                int y1 = top + SIZE / 2 + (int) Math.round(zs.get(i) * pixelsPerBlock);
                int x2 = left + SIZE / 2 + (int) Math.round(xs.get(j) * pixelsPerBlock);
                int y2 = top + SIZE / 2 + (int) Math.round(zs.get(j) * pixelsPerBlock);
                drawLine(guiGraphics, x1, y1, x2, y2, color, cx, cz, panelRadius);
            }
        }
    }

    /**
     * Not-yet-finalized plot/settlement-perimeter stakes -- drawn as small dots (no connecting
     * lines: without a compiled dependency on Settlemynts, Lyfe has no way to read {@code
     * placementIndex}/session grouping off these entities, only their positions -- see {@code
     * ClientGhostMarkerOutlines}' own doc). Real fix for "stakes that aren't finalized yet aren't
     * showing up," 2026-09-29 -- {@link #drawOutlines} only ever sees Cartographyr-registered
     * (i.e. already-finalized) plots/settlements.
     */
    private void drawInProgressStakes(GuiGraphicsExtractor guiGraphics, int left, int top, ClientLevel level, LocalPlayer player, float cx, float cz, int panelRadius) {
        int radius = ClientMinimapState.sampledRadius();
        if (radius <= 0) {
            return;
        }
        double pixelsPerBlock = (double) SIZE / (radius * 2);
        List<int[]> points = new ArrayList<>();
        points.addAll(ClientGhostMarkerOutlines.pointsNear(level, player, GHOST_PLOT_STAKE_ID, radius));
        points.addAll(ClientGhostMarkerOutlines.pointsNear(level, player, GHOST_PERIMETER_STAKE_ID, radius));
        for (int[] point : points) {
            int x = left + SIZE / 2 + (int) Math.round(point[0] * pixelsPerBlock);
            int y = top + SIZE / 2 + (int) Math.round(point[1] * pixelsPerBlock);
            if (isWithinPanel(x, y, cx, cz, panelRadius)) {
                guiGraphics.fill(x - MARKER_DOT_RADIUS, y - MARKER_DOT_RADIUS, x + MARKER_DOT_RADIUS, y + MARKER_DOT_RADIUS, IN_PROGRESS_STAKE_COLOR);
            }
        }
    }

    /**
     * The Building Locator's live preview (user request, 2026-09-29) -- the plot's own buildable-zone
     * outline (yellow, static) plus the currently-aimed proposed footprint's outline (green if it
     * fits, red if it doesn't), both driven entirely by Blueprynts' server-tick-regenerated ghost
     * markers (see {@code construction.BuildingLocatorTicker} there) -- Lyfe only ever reads their
     * positions and floating block color, never computes fit itself.
     */
    private void drawBuildingLocatorPreview(GuiGraphicsExtractor guiGraphics, int left, int top, ClientLevel level, LocalPlayer player, float cx, float cz, int panelRadius) {
        int radius = ClientMinimapState.sampledRadius();
        if (radius <= 0) {
            return;
        }
        double pixelsPerBlock = (double) SIZE / (radius * 2);
        for (ClientGhostMarkerOutlines.ColoredPoint point : ClientGhostMarkerOutlines.pointsWithBlockNear(level, player, GHOST_BUILDING_PREVIEW_ID, radius)) {
            int color;
            if (point.block() == Blocks.LIME_STAINED_GLASS) {
                color = BUILDING_FIT_COLOR;
            } else if (point.block() == Blocks.RED_STAINED_GLASS) {
                color = BUILDING_NO_FIT_COLOR;
            } else {
                color = BUILDING_ZONE_COLOR;
            }
            int x = left + SIZE / 2 + (int) Math.round(point.dx() * pixelsPerBlock);
            int y = top + SIZE / 2 + (int) Math.round(point.dz() * pixelsPerBlock);
            if (isWithinPanel(x, y, cx, cz, panelRadius)) {
                guiGraphics.fill(x - MARKER_DOT_RADIUS, y - MARKER_DOT_RADIUS, x + MARKER_DOT_RADIUS, y + MARKER_DOT_RADIUS, color);
            }
        }
    }

    /**
     * Simple integer walk, one filled pixel per step -- screen-space, not Cartographyr's own
     * block-grid {@code supercoverLine}. Each pixel is skipped once it's outside the circular panel
     * ({@code cx}/{@code cz}/{@code panelRadius}) -- a real bug, 2026-09-29 ("lines are extending
     * outside the circle"): once the square corners stopped being painted over (see this class's own
     * doc on true transparency there), lines that always extended into them became visible poking
     * past the circular border instead of being hidden by the old opaque mask.
     */
    private static void drawLine(GuiGraphicsExtractor guiGraphics, int x1, int y1, int x2, int y2, int color, float cx, float cz, int panelRadius) {
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        if (steps == 0) {
            if (isWithinPanel(x1, y1, cx, cz, panelRadius)) {
                guiGraphics.fill(x1, y1, x1 + 1, y1 + 1, color);
            }
            return;
        }
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            int x = x1 + Math.round((x2 - x1) * t);
            int y = y1 + Math.round((y2 - y1) * t);
            if (isWithinPanel(x, y, cx, cz, panelRadius)) {
                guiGraphics.fill(x, y, x + 1, y + 1, color);
            }
        }
    }

    /** Whether {@code (x, y)} lies within {@code panelRadius} of {@code (cx, cz)} -- valid regardless of any pose rotation currently active, since rotation about that same center never changes a point's distance from it. */
    private static boolean isWithinPanel(int x, int y, float cx, float cz, int panelRadius) {
        float dx = x - cx;
        float dz = y - cz;
        return dx * dx + dz * dz <= (float) panelRadius * panelRadius;
    }
}
