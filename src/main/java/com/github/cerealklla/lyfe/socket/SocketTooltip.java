package com.github.cerealklla.lyfe.socket;

import java.util.List;

import net.minecraft.world.inventory.tooltip.TooltipComponent;

/**
 * The transient "image" half of a socketed item's tooltip (explicit user mockup, 2026-10-18) --
 * carries the real ordered {@link SocketSlot} list straight through, letting {@code
 * client.ClientSocketTooltip} decide how to render each one. Never persisted/networked on its own
 * (the real synced data is {@code ModItems#SOCKET_DATA} on the stack already) -- this is only ever
 * constructed fresh inside {@code mixin.ItemStackSocketTooltipMixin}'s injection into {@code
 * ItemStack#getTooltipImage()}, the same per-render-call shape {@code fishing.CatchBagTooltip}/
 * vanilla's own {@code BundleContents}-backed tooltip already use.
 */
public record SocketTooltip(List<SocketSlot> sockets) implements TooltipComponent {

    public static SocketTooltip of(SocketData data) {
        return new SocketTooltip(data.sockets());
    }
}
