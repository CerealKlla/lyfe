package com.github.cerealklla.lyfe.fishing.client;

import net.minecraft.client.renderer.entity.DisplayRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/**
 * Renders {@code fishing.ShipwreckMarkerEntity} via vanilla's own {@link
 * DisplayRenderer.BlockDisplayRenderer} (its constructor is {@code protected}, so this just
 * exposes it) -- same trivial-subclass shape as Settlemynts' own {@code
 * founding.client.GhostBlockDisplayRenderer}, not reused directly since this feature doesn't need
 * Settlemynts loaded at all. The marker always displays {@link net.minecraft.world.level.block.Blocks#BARRIER},
 * which this renderer draws as genuinely nothing visible, by design.
 */
public final class ShipwreckMarkerRenderer extends DisplayRenderer.BlockDisplayRenderer {

    public ShipwreckMarkerRenderer(EntityRendererProvider.Context context) {
        super(context);
    }
}
