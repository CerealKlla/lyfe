package com.github.cerealklla.lyfe.fishing;

import java.util.ArrayList;
import java.util.List;

import com.github.cerealklla.lyfe.craft.EquipmentTierLadder;
import com.github.cerealklla.lyfe.research.ResearchNoteConstants;
import com.github.cerealklla.lyfe.research.ResearchNoteItem;

import com.mojang.serialization.Codec;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * A fixed, baked-in-at-creation loot payload (design doc Sections E/G, Sunken Treasure Bag and the
 * Locked Chest variants) -- shared by both, since they're the same shape ("a list of real
 * ItemStacks, popped one at a time, never refillable"). Modeled on {@code research.ResearchNoteTarget}'s
 * "baked in once, read everywhere" idiom rather than vanilla Bundle's mutable insert/remove, since
 * neither of these ever accepts insertion.
 */
public record FixedLootContents(List<ItemStack> stacks) {

    public static final FixedLootContents EMPTY = new FixedLootContents(List.of());

    public static final Codec<FixedLootContents> CODEC = ItemStack.CODEC.listOf()
            .xmap(FixedLootContents::new, FixedLootContents::stacks);
    public static final StreamCodec<RegistryFriendlyByteBuf, FixedLootContents> STREAM_CODEC = ItemStack.STREAM_CODEC
            .apply(ByteBufCodecs.list())
            .map(FixedLootContents::new, FixedLootContents::stacks);

    public FixedLootContents {
        stacks = List.copyOf(stacks);
    }

    public boolean isEmpty() {
        return stacks.isEmpty();
    }

    public int totalCount() {
        return stacks.size();
    }

    public RemoveResult removeLast() {
        if (stacks.isEmpty()) {
            return new RemoveResult(this, ItemStack.EMPTY);
        }
        List<ItemStack> next = new ArrayList<>(stacks);
        ItemStack removed = next.remove(next.size() - 1);
        return new RemoveResult(new FixedLootContents(next), removed);
    }

    public record RemoveResult(FixedLootContents contents, ItemStack removed) {
    }

    /** Rolls a Sunken Treasure Bag's payload (design doc Section E). */
    public static FixedLootContents rollSunkenTreasure(RandomSource random) {
        List<ItemStack> stacks = new ArrayList<>();
        addGoldNuggets(stacks, random, FishingConstants.SUNKEN_TREASURE_MIN_GOLD_NUGGETS, FishingConstants.SUNKEN_TREASURE_MAX_GOLD_NUGGETS);
        int noteStacks = randomBetween(random, FishingConstants.SUNKEN_TREASURE_MIN_NOTE_STACKS, FishingConstants.SUNKEN_TREASURE_MAX_NOTE_STACKS);
        addResearchNotes(stacks, random, noteStacks, FishingConstants.SUNKEN_TREASURE_MIN_NOTE_COUNT, FishingConstants.SUNKEN_TREASURE_MAX_NOTE_COUNT);
        int highEnd = randomBetween(random, FishingConstants.SUNKEN_TREASURE_MIN_HIGH_END, FishingConstants.SUNKEN_TREASURE_MAX_HIGH_END);
        addHighEndLoot(stacks, random, highEnd);
        return new FixedLootContents(stacks);
    }

    /** Rolls a Locked Chest's payload (design doc Section G) -- always the full, larger amount, no range on stack count. */
    public static FixedLootContents rollLockedChest(RandomSource random) {
        List<ItemStack> stacks = new ArrayList<>();
        addGoldNuggets(stacks, random, FishingConstants.LOCKED_CHEST_MIN_GOLD_NUGGETS, FishingConstants.LOCKED_CHEST_MAX_GOLD_NUGGETS);
        addResearchNotes(stacks, random, FishingConstants.LOCKED_CHEST_NOTE_STACKS, FishingConstants.LOCKED_CHEST_MIN_NOTE_COUNT, FishingConstants.LOCKED_CHEST_MAX_NOTE_COUNT);
        int highEnd = randomBetween(random, FishingConstants.LOCKED_CHEST_MIN_HIGH_END, FishingConstants.LOCKED_CHEST_MAX_HIGH_END);
        addHighEndLoot(stacks, random, highEnd);
        return new FixedLootContents(stacks);
    }

    private static void addGoldNuggets(List<ItemStack> stacks, RandomSource random, int min, int max) {
        int total = randomBetween(random, min, max);
        int remaining = total;
        while (remaining > 0) {
            int amount = Math.min(64, remaining);
            stacks.add(new ItemStack(Items.GOLD_NUGGET, amount));
            remaining -= amount;
        }
    }

    private static void addResearchNotes(List<ItemStack> stacks, RandomSource random, int stackCount, int minCount, int maxCount) {
        for (int i = 0; i < stackCount; i++) {
            int tier = randomBetween(random, 1, EquipmentTierLadder.MAX_REACHABLE_TIER);
            var resultId = ResearchNoteConstants.randomTargetForTier(tier, random);
            ItemStack note = ResearchNoteItem.createFor(resultId, tier);
            note.setCount(randomBetween(random, minCount, maxCount));
            stacks.add(note);
        }
    }

    private static void addHighEndLoot(List<ItemStack> stacks, RandomSource random, int count) {
        List<net.minecraft.world.item.Item> pool = FishLootPools.HIGH_END_LOOT;
        for (int i = 0; i < count; i++) {
            stacks.add(new ItemStack(pool.get(random.nextInt(pool.size()))));
        }
    }

    private static int randomBetween(RandomSource random, int min, int max) {
        if (max <= min) {
            return min;
        }
        return min + random.nextInt(max - min + 1);
    }
}
