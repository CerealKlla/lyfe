package com.github.cerealklla.lyfe.client;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;

/** Carries the one extra piece a {@link FloatingIconRenderer} needs beyond the base state: the resolved floating icon's own render state. */
public class FloatingIconRenderState extends BlockEntityRenderState {
    public final ItemStackRenderState itemRenderState = new ItemStackRenderState();
    public double yRotDegrees;
}
