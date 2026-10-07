package com.github.cerealklla.lyfe.fishing;

import com.mojang.serialization.Codec;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A caught fish's weight in pounds (design doc, 2026-10-03 -- changed mid-session from an original
 * "length in inches" spec), 2 decimal places. Baked onto the stack at catch time by {@code
 * FishermanListener#onItemFished}, same "bake it in once, read it everywhere" idiom as {@code
 * research.ResearchNoteTarget}. Also the generic marker a fish-type item carries -- {@code
 * CatchBagItem}'s insertion check is simply "does this stack carry a FishWeight component," not a
 * hardcoded item list.
 */
public record FishWeight(double pounds) {

    public static final Codec<FishWeight> CODEC = Codec.DOUBLE.xmap(FishWeight::new, FishWeight::pounds);
    public static final StreamCodec<ByteBuf, FishWeight> STREAM_CODEC =
            ByteBufCodecs.DOUBLE.map(FishWeight::new, FishWeight::pounds);
}
