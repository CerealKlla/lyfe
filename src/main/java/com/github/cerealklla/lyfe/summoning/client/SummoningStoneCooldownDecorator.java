package com.github.cerealklla.lyfe.summoning.client;

import com.github.cerealklla.lyfe.registration.ModItems;
import com.github.cerealklla.lyfe.summoning.SummoningStoneData;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.IItemDecorator;

/**
 * Real user report, 2026-10-25: "even in my inventory it's not visually wiping" -- the Summoning
 * Stone's cooldown is tracked per-stack ({@link SummoningStoneData}), never vanilla's own {@code
 * ItemCooldowns} (same reasoning as {@code recallcinite.RecallciniteData}'s own cooldown -- see that
 * class's own doc), so vanilla's real built-in cooldown swipe never renders for it automatically.
 *
 * <p>Unlike the Recallcinite Totem's own {@code client.RecallciniteHotbarCooldownOverlay} (a
 * reasonable choice there, since that item is always pinned to hotbar slot 8), a Summoning Stone can
 * sit in any inventory slot at all -- including a Caryvan cart's own mount-slot panel. Rather than
 * hand-roll per-screen rendering, this uses the real generic NeoForge hook built for exactly this
 * ("render something on specific items, same place the durability bar/stack count render"):
 * {@link IItemDecorator}, registered via {@code RegisterItemDecorationsEvent} in {@code
 * LyfeModClient}, called automatically by vanilla's own slot-rendering code wherever this item is
 * drawn, in any screen, with the real slot position handed in directly (no hardcoded offset math
 * needed the way the hotbar-only overlay required).
 *
 * <p>Same vanilla-matching "swipe" math as {@code GuiGraphicsExtractor#itemCooldown}'s own real
 * formula (and the Totem overlay's own copy of it): {@code top = y + floor(16*(1-fraction))},
 * {@code bottom = top + ceil(16*fraction)}, fill color {@code 0x7FFFFFFF}.
 */
public final class SummoningStoneCooldownDecorator implements IItemDecorator {

    @Override
    public boolean render(GuiGraphicsExtractor guiGraphics, Font font, ItemStack stack, int xOffset, int yOffset) {
        SummoningStoneData data = stack.get(ModItems.SUMMONING_STONE_DATA.get());
        if (data == null) {
            return false;
        }
        float fraction = data.cooldownFractionRemaining(System.currentTimeMillis());
        if (fraction <= 0.0F) {
            return false;
        }
        int top = yOffset + (int) Math.floor(16.0F * (1.0F - fraction));
        int bottom = top + (int) Math.ceil(16.0F * fraction);
        guiGraphics.fill(xOffset, top, xOffset + 16, bottom, 0x7FFFFFFF);
        return false;
    }
}
