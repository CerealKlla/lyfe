package com.github.cerealklla.lyfe.recallcinite.client;

import com.github.cerealklla.lyfe.recallcinite.RecallciniteData;
import com.github.cerealklla.lyfe.recallcinite.RecallciniteTotemItem;
import com.github.cerealklla.lyfe.registration.ModAttachments;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.gui.GuiLayer;

/**
 * A custom gray "swipe" over the Recallcinite Totem's own hotbar slot while its recall cooldown is
 * active -- mimics vanilla's own item-cooldown overlay pixel-for-pixel ({@code
 * GuiGraphicsExtractor#itemCooldown}'s exact math: {@code top = y + floor(16*(1-fraction))},
 * {@code bottom = top + ceil(16*fraction)}, fill color {@code 0x7FFFFFFF}), but reads {@link
 * RecallciniteData#cooldownFractionRemaining} instead of vanilla's own {@code ItemCooldowns}.
 *
 * <p>This exists because vanilla's real cooldown can't be used at all for this item -- see {@code
 * RecallciniteTotemItem}'s own class doc for the confirmed bug that caused (blocking every
 * interaction, including free binding, during the cooldown window). The user still wanted the
 * familiar visual back once that was explained, hence this: same look, independent data source.
 *
 * <p>Hotbar slot position formula copied from {@code client.gui.Gui#extractItemHotbar} (the
 * built-in hotbar renderer) -- {@code x = screenCenter - 90 + slot*20 + 2}, {@code y =
 * guiHeight() - 16 - 3} -- since there's no public accessor for a hotbar slot's own screen
 * position. If vanilla ever changes that layout, this will silently drift out of alignment; worth
 * rechecking against a live screenshot if the hotbar's own look is ever changed.
 */
public final class RecallciniteHotbarCooldownOverlay implements GuiLayer {

    private static final int PINNED_SLOT = 8;

    @Override
    public void render(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker) {
        Player player = Minecraft.getInstance().player;
        if (player == null || !(player.getInventory().getItem(PINNED_SLOT).getItem() instanceof RecallciniteTotemItem)) {
            return;
        }

        RecallciniteData data = player.getData(ModAttachments.RECALLCINITE_DATA);
        float fraction = data.cooldownFractionRemaining(System.currentTimeMillis());
        if (fraction <= 0.0F) {
            return;
        }

        int screenCenter = guiGraphics.guiWidth() / 2;
        int x = screenCenter - 90 + PINNED_SLOT * 20 + 2;
        int y = guiGraphics.guiHeight() - 16 - 3;
        int top = y + (int) Math.floor(16.0F * (1.0F - fraction));
        int bottom = top + (int) Math.ceil(16.0F * fraction);
        guiGraphics.fill(x, top, x + 16, bottom, 0x7FFFFFFF);
    }
}
