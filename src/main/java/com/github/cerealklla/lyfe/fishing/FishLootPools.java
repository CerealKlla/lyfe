package com.github.cerealklla.lyfe.fishing;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;

/**
 * Bonus-loot item pools for the Fisherman skill (design doc, 2026-10-03). Grouped by a few broad
 * biome categories rather than per-biome -- no existing biome-loot catalog exists anywhere in this
 * mod to extend (confirmed via research), and per-biome granularity would be well past this pass's
 * "don't over-engineer a first version" bar, same as every other random-pool in this mod.
 *
 * <p>{@link #categoryFor} resolves a biome's category off its registry path rather than a vanilla
 * {@code BiomeTags} constant -- simpler and avoids chasing down this version's exact tag set for a
 * one-off keyword match.
 */
public final class FishLootPools {

    private FishLootPools() {
    }

    public enum BiomeCategory {
        OCEAN_RIVER, SWAMP, JUNGLE, DESERT, COLD, GENERIC
    }

    public static BiomeCategory categoryFor(Holder<Biome> biome) {
        String path = biome.unwrapKey().map(ResourceKey::identifier).map(id -> id.getPath()).orElse("");
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.contains("swamp") || lower.contains("mangrove")) {
            return BiomeCategory.SWAMP;
        }
        if (lower.contains("jungle")) {
            return BiomeCategory.JUNGLE;
        }
        if (lower.contains("desert") || lower.contains("badlands") || lower.contains("mesa")) {
            return BiomeCategory.DESERT;
        }
        if (lower.contains("snowy") || lower.contains("frozen") || lower.contains("ice") || lower.contains("cold") || lower.contains("taiga")) {
            return BiomeCategory.COLD;
        }
        if (lower.contains("ocean") || lower.contains("river") || lower.contains("beach") || lower.contains("coast")) {
            return BiomeCategory.OCEAN_RIVER;
        }
        return BiomeCategory.GENERIC;
    }

    public static List<Item> poolFor(BiomeCategory category) {
        return switch (category) {
            case OCEAN_RIVER -> List.of(Items.KELP, Items.SEAGRASS, Items.PRISMARINE_CRYSTALS, Items.PRISMARINE_SHARD, Items.NAUTILUS_SHELL);
            case SWAMP -> List.of(Items.SLIME_BALL, Items.LILY_PAD, Items.VINE, Items.MUDDY_MANGROVE_ROOTS, Items.SUGAR_CANE);
            case JUNGLE -> List.of(Items.COCOA_BEANS, Items.BAMBOO, Items.MELON_SLICE, Items.SWEET_BERRIES, Items.VINE);
            case DESERT -> List.of(Items.CACTUS, Items.DEAD_BUSH, Items.SAND, Items.GLASS_BOTTLE, Items.BONE);
            case COLD -> List.of(Items.SNOWBALL, Items.ICE, Items.BLUE_ICE, Items.POWDER_SNOW_BUCKET, Items.WHITE_WOOL);
            case GENERIC -> List.of(Items.WHEAT, Items.CARROT, Items.POTATO, Items.APPLE, Items.SUGAR, Items.GLOWSTONE_DUST);
        };
    }

    /** Seed list of "boss-drop quality" items -- Sunken Treasure Bag / Locked Chest high-end slots. */
    public static final List<Item> HIGH_END_LOOT = List.of(
            Items.NETHER_STAR, Items.DRAGON_BREATH, Items.ELYTRA, Items.TOTEM_OF_UNDYING,
            Items.ENCHANTED_GOLDEN_APPLE, Items.NETHERITE_INGOT, Items.ANCIENT_DEBRIS);

    /** Dimension-themed loot for the custom Nether catch loop (design doc Section F). */
    public static final List<Item> NETHER_CATCH = List.of(
            Items.MAGMA_CREAM, Items.BLAZE_ROD, Items.GHAST_TEAR, Items.QUARTZ, Items.GLOWSTONE_DUST,
            Items.SHULKER_SHELL, Items.WARPED_FUNGUS, Items.CRIMSON_FUNGUS, Items.ANCIENT_DEBRIS);

    /** Dimension-themed loot for the custom End catch loop. */
    public static final List<Item> END_CATCH = List.of(
            Items.CHORUS_FRUIT, Items.POPPED_CHORUS_FRUIT, Items.ENDER_PEARL, Items.SHULKER_SHELL,
            Items.ECHO_SHARD, Items.DRAGON_BREATH);

    public static Optional<List<Item>> voidCatchFor(net.minecraft.world.level.Level level) {
        if (level.dimension().equals(net.minecraft.world.level.Level.NETHER)) {
            return Optional.of(NETHER_CATCH);
        }
        if (level.dimension().equals(net.minecraft.world.level.Level.END)) {
            return Optional.of(END_CATCH);
        }
        return Optional.empty();
    }
}
