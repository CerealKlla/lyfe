package com.github.cerealklla.lyfe.socket.mixin;

import java.util.Optional;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.github.cerealklla.lyfe.registration.ModItems;
import com.github.cerealklla.lyfe.socket.SocketData;
import com.github.cerealklla.lyfe.socket.SocketTooltip;

import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

/**
 * Shows the socket-icon row in a socketed item's tooltip (explicit user mockup, 2026-10-18).
 * {@code ItemStack#getTooltipImage()} always calls {@code getItem().getTooltipImage(this)} --
 * confirmed against the decompiled source, a pure virtual dispatch with no generic/event-based
 * override seam -- so there's no way to inject this for an arbitrary vanilla/modded weapon/tool/
 * armor item (Lyfe doesn't own those classes) without a Mixin. This is Lyfe's second-ever Mixin,
 * added only after the user was explicitly offered the real tradeoff (same category of fix as {@code
 * durability.mixin.ItemStackUnbreakableMixin}, Lyfe's first) and chose it over a text-only
 * approximation.
 *
 * <p>Deliberately does NOT try to combine with whatever the underlying item's own real tooltip image
 * might have been (no vanilla/modded socketable item in this suite has one of its own today, so
 * there's nothing to preserve) -- a socketed stack's image is unconditionally the socket row.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackSocketTooltipMixin {

    @Inject(method = "getTooltipImage()Ljava/util/Optional;", at = @At("HEAD"), cancellable = true)
    private void lyfe$socketTooltip(CallbackInfoReturnable<Optional<TooltipComponent>> cir) {
        ItemStack self = (ItemStack) (Object) this;
        SocketData data = self.get(ModItems.SOCKET_DATA.get());
        if (data == null || data.sockets().isEmpty()) {
            return;
        }
        cir.setReturnValue(Optional.of(SocketTooltip.of(data)));
    }
}
