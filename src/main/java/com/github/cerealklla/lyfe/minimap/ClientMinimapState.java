package com.github.cerealklla.lyfe.minimap;

import java.util.List;

import com.mojang.blaze3d.platform.NativeImage;

import com.github.cerealklla.lyfe.LyfeMod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Client-side holder for the minimap's current terrain texture, sampling state, and the latest
 * nearby-entity outlines from the server ({@link MinimapEntitiesPayload}). Mirrors {@code
 * location.ClientLocationState}'s "harmless to classload on a dedicated server" shape -- nothing
 * here touches real client-only objects (a {@link DynamicTexture}) except from methods only ever
 * called from a payload handler's lambda body or {@link MinimapOverlay#render}, both of which only
 * ever execute on an actual client.
 */
public final class ClientMinimapState {

    private static final Identifier TEXTURE_ID = Identifier.fromNamespaceAndPath(LyfeMod.MODID, "minimap_terrain");

    private static DynamicTexture texture;
    private static volatile boolean sampling;
    private static int sampledCenterX = Integer.MIN_VALUE;
    private static int sampledCenterZ = Integer.MIN_VALUE;
    private static int sampledRadius = -1;
    private static int sampledEffectiveRadius = -1;

    private static List<MinimapEntitiesPayload.Outline> outlines = List.of();

    // Auto-Rotate mode, wired up 2026-09-29 for a live test (user request: "turn on rotating mini
    // map for a test... I suspect no one will ever want that with this refresh rate"), which at the
    // time stayed off by default since that request was framed as a one-off test, not a standing
    // "make this the default" instruction. Flipped to the real default 2026-09-30 per explicit
    // follow-up request.
    private static boolean autoRotate = true;

    public static boolean autoRotate() {
        return autoRotate;
    }

    public static void setAutoRotate(boolean value) {
        autoRotate = value;
    }

    private ClientMinimapState() {
    }

    public static boolean isSampling() {
        return sampling;
    }

    public static void beginSampling() {
        sampling = true;
    }

    /** Called on the render thread once {@link ClientTerrainSampler#sampleAsync} completes -- uploads the image and (re)registers the texture under a single, reused Identifier. */
    public static void setTexture(NativeImage image, int centerX, int centerZ, int radius) {
        if (texture == null) {
            texture = new DynamicTexture(() -> "lyfe_minimap", image);
            Minecraft.getInstance().getTextureManager().register(TEXTURE_ID, texture);
        } else {
            texture.setPixels(image);
        }
        texture.upload();
        sampledCenterX = centerX;
        sampledCenterZ = centerZ;
        sampledRadius = radius;
        // The real, post-clamp coverage radius -- see ClientTerrainSampler#effectiveRadius's own doc
        // (2026-10-09 fix). Kept separate from sampledRadius itself, which MinimapOverlay#maybeResample
        // still needs to be the raw REQUESTED value for its own "did the zoom level actually change"
        // comparison -- using the effective value there instead would falsely compare equal/unequal
        // whenever the clamp kicks in regardless of the real requested radius, causing either a
        // resample-every-tick loop or a stuck-stale-zoom bug depending on which way it drifted.
        sampledEffectiveRadius = ClientTerrainSampler.effectiveRadius(radius, ClientTerrainSampler.IMAGE_SIZE);
        sampling = false;
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

    public static int sampledRadius() {
        return sampledRadius;
    }

    /** The real, post-clamp block radius the current texture actually covers -- see {@link #setTexture}'s own doc for why this differs from {@link #sampledRadius()}. Every overlay's own blocks-per-pixel math must use this one, not the raw requested radius. */
    public static int sampledEffectiveRadius() {
        return sampledEffectiveRadius;
    }

    public static void setOutlines(List<MinimapEntitiesPayload.Outline> newOutlines) {
        outlines = newOutlines;
    }

    public static List<MinimapEntitiesPayload.Outline> outlines() {
        return outlines;
    }
}
