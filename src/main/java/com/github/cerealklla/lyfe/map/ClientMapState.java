package com.github.cerealklla.lyfe.map;

import java.util.List;

import com.mojang.blaze3d.platform.NativeImage;

import com.github.cerealklla.lyfe.LyfeMod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Client-side holder for the full-screen Map's own sampled terrain texture and the latest
 * settlement-dot list from the server -- the same shape as {@code minimap.ClientMinimapState}, kept
 * separate (own texture/Identifier, own sampled-center/radius) since the Map is a different screen
 * with a different radius/zoom, opened far less often than the always-on minimap. No rotation state
 * -- the Map stays north-up fixed, not asked for in the original spec.
 *
 * <p>Built from {@code map.cache.TerrainCache} (2026-10-07) rather than a live sample -- synchronous,
 * not async, since reading the cache is plain in-memory array lookups, not block-state queries (see
 * {@code map.client.MapScreen}'s own doc); there is deliberately no "sampling in flight" state here
 * anymore.
 *
 * <p><b>The underlying GPU texture is allocated exactly once, at a fixed {@link #CANVAS_SIZE}, and
 * never resized</b> -- a real bug found live, 2026-10-07: an earlier version recreated/re-registered
 * a differently-sized {@link DynamicTexture} every time the zoom level changed the sampled image's
 * logical pixel dimensions, which produced a visibly tiled/repeating texture (the GPU sampling wrapped
 * instead of clamping, almost certainly because the live-rendered quad's declared texture dimensions
 * briefly disagreed with the actual just-replaced GPU resource during/after a resize). Zooming now
 * only changes how much of the fixed canvas is actually drawn into ({@link #setTexture}'s {@code
 * usedWidth}/{@code usedHeight}, always {@code <= CANVAS_SIZE}) and how much of it {@code
 * map.client.MapScreen}'s blit call samples from -- the texture object itself is stable for the whole
 * session.
 */
public final class ClientMapState {

    private static final Identifier TEXTURE_ID = Identifier.fromNamespaceAndPath(LyfeMod.MODID, "map_terrain");

    /** Fixed GPU texture size -- see this class's own doc for why this never changes. */
    public static final int CANVAS_SIZE = 512;

    private static DynamicTexture texture;
    private static int sampledCenterX = Integer.MIN_VALUE;
    private static int sampledCenterZ = Integer.MIN_VALUE;
    // Separate X/Z radius and *logical* used-portion-of-the-canvas dimensions, 2026-10-07 -- the Map
    // samples a real-world area proportional to its own viewport aspect ratio (wider window -> more
    // world shown horizontally, same blocks-per-pixel scale both axes) rather than stretching a
    // square sample, which visibly distorted terrain on a widescreen window (user report, "slightly
    // stretched horizontally"). These are always <= CANVAS_SIZE, never the GPU texture's own size.
    private static int sampledRadiusX = -1;
    private static int sampledRadiusZ = -1;
    private static int sampledImageWidth = -1;
    private static int sampledImageHeight = -1;

    private static List<MapSettlementsPayload.Entry> settlements = List.of();

    private ClientMapState() {
    }

    /** {@code image} must always be exactly {@code CANVAS_SIZE x CANVAS_SIZE} -- {@code usedWidth}/{@code usedHeight} describe how much of it is actually meaningful content (the rest is expected to be fully transparent). */
    public static void setTexture(NativeImage image, int centerX, int centerZ, int radiusX, int radiusZ, int usedWidth, int usedHeight) {
        if (texture == null) {
            texture = new DynamicTexture(() -> "lyfe_map", image);
            Minecraft.getInstance().getTextureManager().register(TEXTURE_ID, texture);
        } else {
            texture.setPixels(image);
        }
        texture.upload();
        sampledCenterX = centerX;
        sampledCenterZ = centerZ;
        sampledRadiusX = radiusX;
        sampledRadiusZ = radiusZ;
        sampledImageWidth = usedWidth;
        sampledImageHeight = usedHeight;
    }

    public static int sampledImageWidth() {
        return sampledImageWidth;
    }

    public static int sampledImageHeight() {
        return sampledImageHeight;
    }

    public static Identifier textureId() {
        return TEXTURE_ID;
    }

    public static boolean hasTexture() {
        return texture != null;
    }

    public static int sampledCenterX() {
        return sampledCenterX;
    }

    public static int sampledCenterZ() {
        return sampledCenterZ;
    }

    public static int sampledRadiusX() {
        return sampledRadiusX;
    }

    public static int sampledRadiusZ() {
        return sampledRadiusZ;
    }

    public static void setSettlements(List<MapSettlementsPayload.Entry> newSettlements) {
        settlements = newSettlements;
    }

    public static List<MapSettlementsPayload.Entry> settlements() {
        return settlements;
    }
}
