package com.github.cerealklla.lyfe.registration;

import com.github.cerealklla.lyfe.LyfeMod;
import com.github.cerealklla.lyfe.cook.CookingStructureBlockEntity;
import com.github.cerealklla.lyfe.craft.CraftingStructureBlockEntity;
import com.github.cerealklla.lyfe.fishing.FishCleaningStationBlockEntity;
import com.github.cerealklla.lyfe.research.ResearchBenchBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {

    private ModBlockEntities() {
    }

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, LyfeMod.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ResearchBenchBlockEntity>> RESEARCH_BENCH =
            BLOCK_ENTITIES.register("research_bench",
                    () -> new BlockEntityType<>(ResearchBenchBlockEntity::new, ModBlocks.RESEARCH_BENCH.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CraftingStructureBlockEntity>> CRAFTING_STATION =
            BLOCK_ENTITIES.register("crafting_station",
                    () -> new BlockEntityType<>((pos, state) -> new CraftingStructureBlockEntity(pos, state, 1), ModBlocks.CRAFTING_STATION.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CraftingStructureBlockEntity>> CRAFTING_MAT =
            BLOCK_ENTITIES.register("crafting_mat",
                    () -> new BlockEntityType<>((pos, state) -> new CraftingStructureBlockEntity(pos, state, 2), ModBlocks.CRAFTING_MAT.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CraftingStructureBlockEntity>> TINKER_BENCH =
            BLOCK_ENTITIES.register("tinker_bench",
                    () -> new BlockEntityType<>((pos, state) -> new CraftingStructureBlockEntity(pos, state, 3), ModBlocks.TINKER_BENCH.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CraftingStructureBlockEntity>> CRAFTING_TABLE_LYFE =
            BLOCK_ENTITIES.register("crafting_table_lyfe",
                    () -> new BlockEntityType<>((pos, state) -> new CraftingStructureBlockEntity(pos, state, 4), ModBlocks.CRAFTING_TABLE_LYFE.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CraftingStructureBlockEntity>> ENGINEERS_BENCH =
            BLOCK_ENTITIES.register("engineers_bench",
                    () -> new BlockEntityType<>((pos, state) -> new CraftingStructureBlockEntity(pos, state, 5), ModBlocks.ENGINEERS_BENCH.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CookingStructureBlockEntity>> COOKING_STATION =
            BLOCK_ENTITIES.register("cooking_station",
                    () -> new BlockEntityType<>((pos, state) -> new CookingStructureBlockEntity(pos, state, 1), ModBlocks.COOKING_STATION.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CookingStructureBlockEntity>> GRILL =
            BLOCK_ENTITIES.register("grill",
                    () -> new BlockEntityType<>((pos, state) -> new CookingStructureBlockEntity(pos, state, 2), ModBlocks.GRILL.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CookingStructureBlockEntity>> STOVE =
            BLOCK_ENTITIES.register("stove",
                    () -> new BlockEntityType<>((pos, state) -> new CookingStructureBlockEntity(pos, state, 3), ModBlocks.STOVE.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CookingStructureBlockEntity>> OVEN =
            BLOCK_ENTITIES.register("oven",
                    () -> new BlockEntityType<>((pos, state) -> new CookingStructureBlockEntity(pos, state, 4), ModBlocks.OVEN.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CookingStructureBlockEntity>> CHEF_SET =
            BLOCK_ENTITIES.register("chef_set",
                    () -> new BlockEntityType<>((pos, state) -> new CookingStructureBlockEntity(pos, state, 5), ModBlocks.CHEF_SET.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FishCleaningStationBlockEntity>> FISH_CLEANING_STATION =
            BLOCK_ENTITIES.register("fish_cleaning_station",
                    () -> new BlockEntityType<>(FishCleaningStationBlockEntity::new, ModBlocks.FISH_CLEANING_STATION.get()));
}
