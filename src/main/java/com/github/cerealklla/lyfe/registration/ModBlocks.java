package com.github.cerealklla.lyfe.registration;

import com.github.cerealklla.lyfe.LyfeMod;
import com.github.cerealklla.lyfe.cook.CookingStructureBlock;
import com.github.cerealklla.lyfe.craft.CraftingStructureBlock;
import com.github.cerealklla.lyfe.fishing.FishCleaningStationBlock;
import com.github.cerealklla.lyfe.research.ResearchBenchBlock;
import com.github.cerealklla.lyfe.rest.StoolBlock;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Lyfe's first player-placeable blocks (design doc Section 19) -- Research Bench + Tiers 2-5 of the crafting-structure ladder. Tier 1 needs no block at all. */
public final class ModBlocks {

    private ModBlocks() {
    }

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(LyfeMod.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(LyfeMod.MODID);

    public static final DeferredBlock<ResearchBenchBlock> RESEARCH_BENCH = BLOCKS.register(
            "research_bench",
            id -> new ResearchBenchBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.5F)
                    .sound(SoundType.WOOD)
                    .setId(ResourceKey.create(Registries.BLOCK, id))));

    // Tier 1 -- a real, always-known vanilla-style recipe (see src/main/resources/data/lyfe/recipe),
    // not research-gated. Confirmed 2026-10-02: the design doc's original "no block, use your own
    // inventory" idea for Tier 1 was replaced with a real craftable structure, same shape as Tiers
    // 2-5, so there's one consistent "place a structure, upgrade it" flow instead of a special case.
    public static final DeferredBlock<CraftingStructureBlock> CRAFTING_STATION = BLOCKS.register(
            "crafting_station",
            id -> new CraftingStructureBlock(1, BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.5F)
                    .sound(SoundType.WOOD)
                    .setId(ResourceKey.create(Registries.BLOCK, id))));

    public static final DeferredBlock<CraftingStructureBlock> CRAFTING_MAT = BLOCKS.register(
            "crafting_mat",
            id -> new CraftingStructureBlock(2, BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.5F)
                    .sound(SoundType.WOOD)
                    .setId(ResourceKey.create(Registries.BLOCK, id))));

    public static final DeferredBlock<CraftingStructureBlock> TINKER_BENCH = BLOCKS.register(
            "tinker_bench",
            id -> new CraftingStructureBlock(3, BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.5F)
                    .sound(SoundType.WOOD)
                    .setId(ResourceKey.create(Registries.BLOCK, id))));

    public static final DeferredBlock<CraftingStructureBlock> CRAFTING_TABLE_LYFE = BLOCKS.register(
            "crafting_table_lyfe",
            id -> new CraftingStructureBlock(4, BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.5F)
                    .sound(SoundType.WOOD)
                    .setId(ResourceKey.create(Registries.BLOCK, id))));

    public static final DeferredBlock<CraftingStructureBlock> ENGINEERS_BENCH = BLOCKS.register(
            "engineers_bench",
            id -> new CraftingStructureBlock(5, BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.5F)
                    .sound(SoundType.WOOD)
                    .setId(ResourceKey.create(Registries.BLOCK, id))));

    // Design doc, 2026-10-03 user request -- "first pass" sit-able furniture (see StoolBlock's own
    // doc). Plain wood properties, same bar as every other custom block here.
    public static final DeferredBlock<StoolBlock> STOOL = BLOCKS.register(
            "stool",
            id -> new StoolBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.0F)
                    .sound(SoundType.WOOD)
                    .setId(ResourceKey.create(Registries.BLOCK, id))));

    // "Clean a fish" (design doc, 2026-10-03 user request) -- see FishCleaningStationBlock's own doc.
    public static final DeferredBlock<FishCleaningStationBlock> FISH_CLEANING_STATION = BLOCKS.register(
            "fish_cleaning_station",
            id -> new FishCleaningStationBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.0F)
                    .sound(SoundType.WOOD)
                    .setId(ResourceKey.create(Registries.BLOCK, id))));

    // Cooking overhaul (design doc Section 19.3/19.6, 2026-10-03) -- 5 new custom blocks, direct
    // structural mirror of the 5 crafting-structure blocks above. All new (not vanilla's real
    // Blocks.CAMPFIRE, which can't host a custom BlockEntity) -- see CookingStructureBlock's doc.
    public static final DeferredBlock<CookingStructureBlock> COOKING_STATION = BLOCKS.register(
            "cooking_station",
            id -> new CookingStructureBlock(1, BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.0F)
                    .sound(SoundType.WOOD)
                    .setId(ResourceKey.create(Registries.BLOCK, id))));

    public static final DeferredBlock<CookingStructureBlock> GRILL = BLOCKS.register(
            "grill",
            id -> new CookingStructureBlock(2, BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0F)
                    .sound(SoundType.METAL)
                    .setId(ResourceKey.create(Registries.BLOCK, id))));

    public static final DeferredBlock<CookingStructureBlock> STOVE = BLOCKS.register(
            "stove",
            id -> new CookingStructureBlock(3, BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0F)
                    .sound(SoundType.METAL)
                    .setId(ResourceKey.create(Registries.BLOCK, id))));

    public static final DeferredBlock<CookingStructureBlock> OVEN = BLOCKS.register(
            "oven",
            id -> new CookingStructureBlock(4, BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5F)
                    .sound(SoundType.METAL)
                    .setId(ResourceKey.create(Registries.BLOCK, id))));

    public static final DeferredBlock<CookingStructureBlock> CHEF_SET = BLOCKS.register(
            "chef_set",
            id -> new CookingStructureBlock(5, BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5F)
                    .sound(SoundType.METAL)
                    .setId(ResourceKey.create(Registries.BLOCK, id))));

    public static final DeferredItem<BlockItem> RESEARCH_BENCH_ITEM = ITEMS.registerSimpleBlockItem(RESEARCH_BENCH);
    public static final DeferredItem<BlockItem> CRAFTING_STATION_ITEM = ITEMS.registerSimpleBlockItem(CRAFTING_STATION);
    public static final DeferredItem<BlockItem> CRAFTING_MAT_ITEM = ITEMS.registerSimpleBlockItem(CRAFTING_MAT);
    public static final DeferredItem<BlockItem> TINKER_BENCH_ITEM = ITEMS.registerSimpleBlockItem(TINKER_BENCH);
    public static final DeferredItem<BlockItem> CRAFTING_TABLE_LYFE_ITEM = ITEMS.registerSimpleBlockItem(CRAFTING_TABLE_LYFE);
    public static final DeferredItem<BlockItem> ENGINEERS_BENCH_ITEM = ITEMS.registerSimpleBlockItem(ENGINEERS_BENCH);
    public static final DeferredItem<BlockItem> STOOL_ITEM = ITEMS.registerSimpleBlockItem(STOOL);
    public static final DeferredItem<BlockItem> FISH_CLEANING_STATION_ITEM = ITEMS.registerSimpleBlockItem(FISH_CLEANING_STATION);
    public static final DeferredItem<BlockItem> COOKING_STATION_ITEM = ITEMS.registerSimpleBlockItem(COOKING_STATION);
    public static final DeferredItem<BlockItem> GRILL_ITEM = ITEMS.registerSimpleBlockItem(GRILL);
    public static final DeferredItem<BlockItem> STOVE_ITEM = ITEMS.registerSimpleBlockItem(STOVE);
    public static final DeferredItem<BlockItem> OVEN_ITEM = ITEMS.registerSimpleBlockItem(OVEN);
    public static final DeferredItem<BlockItem> CHEF_SET_ITEM = ITEMS.registerSimpleBlockItem(CHEF_SET);
}
