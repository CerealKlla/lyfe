package com.github.cerealklla.lyfe.socket.client;

import com.github.cerealklla.lyfe.socket.GemItem;
import com.github.cerealklla.lyfe.socket.RuneItem;
import com.github.cerealklla.lyfe.socket.SocketSlot;
import com.github.cerealklla.lyfe.socket.SocketTooltip;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * See {@link SocketTooltip}. A deliberately simpler cousin of {@code fishing.client.ClientCatchBagTooltip}/
 * vanilla's own {@code ClientBundleTooltip} -- always exactly one row, every socket shown (explicit
 * user request: "make sure all 5 would be visible at once, not just the 4 the bundle allows" -- no
 * grid wrapping, no "+N hidden" overflow text, since the real max is 5, not Bundle's open-ended item
 * count). An empty socket still draws its backdrop slot with no item in it, so the row always shows
 * the item's real total socket count at a glance, not just how many are filled.
 */
public final class ClientSocketTooltip implements ClientTooltipComponent {

    private static final Identifier SLOT_BACKGROUND_SPRITE = Identifier.withDefaultNamespace("container/bundle/slot_background");
    private static final int SLOT_SIZE = 24;

    private final SocketTooltip tooltip;

    public ClientSocketTooltip(SocketTooltip tooltip) {
        this.tooltip = tooltip;
    }

    @Override
    public int getWidth(Font font) {
        return tooltip.sockets().size() * SLOT_SIZE;
    }

    @Override
    public int getHeight(Font font) {
        return SLOT_SIZE;
    }

    @Override
    public boolean showTooltipWithItemInHand() {
        return true;
    }

    @Override
    public void extractImage(Font font, int x, int y, int w, int h, GuiGraphicsExtractor graphics) {
        var sockets = tooltip.sockets();
        for (int i = 0; i < sockets.size(); i++) {
            int drawX = x + i * SLOT_SIZE;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_BACKGROUND_SPRITE, drawX, y, SLOT_SIZE, SLOT_SIZE);
            ItemStack icon = iconFor(sockets.get(i));
            if (!icon.isEmpty()) {
                graphics.item(icon, drawX + 4, y + 4, i);
                graphics.itemDecorations(font, icon, drawX + 4, y + 4);
            }
        }
    }

    private static ItemStack iconFor(SocketSlot slot) {
        return switch (slot.kind()) {
            case GEM -> GemItem.create(slot.gemType().orElseThrow(), slot.gemQuality().orElseThrow());
            case RUNE -> RuneItem.create(slot.runeId().orElseThrow());
            case EMPTY -> ItemStack.EMPTY;
        };
    }
}
