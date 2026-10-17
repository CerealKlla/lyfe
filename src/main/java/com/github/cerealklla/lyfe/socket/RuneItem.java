package com.github.cerealklla.lyfe.socket;

import java.util.function.Consumer;

import com.github.cerealklla.lyfe.registration.ModItems;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * A single registered Item for all 30 {@link RuneId}s -- "no quality or variability" (explicit user
 * spec), so unlike Gems there's no second axis to combine against; one shared texture/model, the
 * specific rune is just a stored component, same reasoning as {@link GemItem}'s own doc.
 */
public class RuneItem extends Item {

    public RuneItem(Properties properties) {
        super(properties);
    }

    public static ItemStack create(RuneId id) {
        ItemStack stack = new ItemStack(ModItems.RUNE.get());
        stack.set(ModItems.RUNE_ID.get(), id);
        return stack;
    }

    /** {@code null} if {@code stack} isn't a Rune item, or has no {@code RUNE_ID} set (shouldn't normally happen). */
    public static RuneId runeIdOf(ItemStack stack) {
        return stack.is(ModItems.RUNE.get()) ? stack.get(ModItems.RUNE_ID.get()) : null;
    }

    @Override
    public Component getName(ItemStack stack) {
        RuneId id = runeIdOf(stack);
        return Component.literal(id != null ? id.displayName() + " Rune" : "Rune");
    }

    /** "Pick up the gem [or rune]... click on an item with an open socket" -- see {@link SocketScoopHandler}'s own doc. */
    @Override
    public boolean overrideStackedOnOther(ItemStack self, Slot slot, ClickAction clickAction, Player player) {
        RuneId id = runeIdOf(self);
        if (id == null) {
            return false;
        }
        return SocketScoopHandler.tryScoopInto(self, slot, clickAction, player, SocketSlot.ofRune(id));
    }

    /** Real report, 2026-10-17: the tooltip never said what socketing a Rune actually does. */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag tooltipFlag) {
        RuneId id = runeIdOf(stack);
        if (id == null) {
            return;
        }
        builder.accept(Component.literal("Socket into an item with an open socket.").withStyle(ChatFormatting.GRAY));
        RuneCatalog.RuneStat stat = RuneCatalog.of(id);
        if (stat != null) {
            String sign = stat.amount() >= 0 ? "+" : "";
            builder.accept(Component.literal(sign + stat.amount() + " " + stat.shortLabel()).withStyle(ChatFormatting.BLUE));
        }
    }
}
