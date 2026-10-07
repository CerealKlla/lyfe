package com.github.cerealklla.lyfe.rest.client;

import net.minecraft.client.renderer.entity.DisplayRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/**
 * Trivial subclass exposing vanilla's own {@code protected} {@link DisplayRenderer.BlockDisplayRenderer}
 * constructor (same reasoning as {@code settlemynts.founding.client.GhostBlockDisplayRenderer}) --
 * {@link com.github.cerealklla.lyfe.rest.SeatEntity} is a {@code Display.BlockDisplay} that's never
 * given a block state, so this renders nothing at all; it only exists because every registered
 * entity type needs *some* renderer or the client crashes.
 */
public final class SeatEntityRenderer extends DisplayRenderer.BlockDisplayRenderer {

    public SeatEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }
}
