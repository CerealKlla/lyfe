package com.github.cerealklla.lyfe.fishing;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import com.github.cerealklla.lyfe.registration.ModItems;

/**
 * The Catch Bag's storage (design doc Section H, 2026-10-03). Directly templated off Yconomics'
 * {@code CoinPurseContents} (composition over vanilla's {@code BundleItem}, not a subclass of it),
 * with two changes: restricted to items carrying a {@link FishWeight} component (not a hardcoded
 * item list), and capacity tracked as total pounds rather than item count/vanilla's weight-fraction
 * system -- a mid-session correction from the user ("max of 100 pounds of fish").
 *
 * <p>All-or-nothing insert, same as {@code CoinPurseContents#insert} -- a candidate stack that would
 * push the total over the cap is rejected whole, never partially split.
 */
public record CatchBagContents(List<ItemStack> stacks) {

    public static final CatchBagContents EMPTY = new CatchBagContents(List.of());

    public static final Codec<CatchBagContents> CODEC = ItemStack.CODEC.listOf()
            .xmap(CatchBagContents::new, CatchBagContents::stacks);
    public static final StreamCodec<RegistryFriendlyByteBuf, CatchBagContents> STREAM_CODEC = ItemStack.STREAM_CODEC
            .apply(ByteBufCodecs.list())
            .map(CatchBagContents::new, CatchBagContents::stacks);

    public CatchBagContents {
        stacks = List.copyOf(stacks);
    }

    public boolean isEmpty() {
        return stacks.isEmpty();
    }

    public static boolean isFish(ItemStack stack) {
        return stack.get(ModItems.FISH_WEIGHT.get()) != null;
    }

    public double totalPounds() {
        double total = 0.0;
        for (ItemStack stack : stacks) {
            FishWeight weight = stack.get(ModItems.FISH_WEIGHT.get());
            if (weight != null) {
                total += weight.pounds() * stack.getCount();
            }
        }
        return total;
    }

    /** All-or-nothing: rejects {@code incoming} whole (returns {@code this} unchanged, {@code inserted=false}) if it would exceed the cap. */
    public InsertResult insert(ItemStack incoming) {
        if (!isFish(incoming) || incoming.isEmpty()) {
            return new InsertResult(this, false);
        }
        FishWeight weight = incoming.get(ModItems.FISH_WEIGHT.get());
        double addedPounds = weight.pounds() * incoming.getCount();
        if (totalPounds() + addedPounds > FishingConstants.CATCH_BAG_MAX_POUNDS) {
            return new InsertResult(this, false);
        }
        List<ItemStack> next = new ArrayList<>(stacks);
        next.add(incoming.copy());
        return new InsertResult(new CatchBagContents(next), true);
    }

    /** Removes the most-recently-added stack whole (LIFO, matching vanilla Bundle#removeOne). */
    public RemoveResult removeLast() {
        if (stacks.isEmpty()) {
            return new RemoveResult(this, ItemStack.EMPTY);
        }
        List<ItemStack> next = new ArrayList<>(stacks);
        ItemStack removed = next.remove(next.size() - 1);
        return new RemoveResult(new CatchBagContents(next), removed);
    }

    public record InsertResult(CatchBagContents contents, boolean inserted) {
    }

    public record RemoveResult(CatchBagContents contents, ItemStack removed) {
    }
}
