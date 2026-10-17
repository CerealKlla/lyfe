package com.github.cerealklla.lyfe.socket;

import com.github.cerealklla.lyfe.registration.ModItems;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

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
}
