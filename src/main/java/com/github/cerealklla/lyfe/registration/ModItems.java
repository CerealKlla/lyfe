package com.github.cerealklla.lyfe.registration;

import java.util.HashMap;
import java.util.Map;

import com.github.cerealklla.lyfe.LyfeMod;
import com.github.cerealklla.lyfe.fishing.CatchBagContents;
import com.github.cerealklla.lyfe.fishing.CatchBagItem;
import com.github.cerealklla.lyfe.fishing.FishWeight;
import com.github.cerealklla.lyfe.fishing.FishingConstants;
import com.github.cerealklla.lyfe.fishing.FixedLootContents;
import com.github.cerealklla.lyfe.fishing.KeyItem;
import com.github.cerealklla.lyfe.fishing.LockedChestItem;
import com.github.cerealklla.lyfe.fishing.SunkenTreasureItem;
import com.github.cerealklla.lyfe.cook.RecipeNoteItem;
import com.github.cerealklla.lyfe.cook.RecipeNoteTarget;
import com.github.cerealklla.lyfe.knowledge.KnowledgeReference;
import com.github.cerealklla.lyfe.research.ResearchNoteItem;
import com.github.cerealklla.lyfe.research.ResearchNoteTarget;

import com.mojang.serialization.Codec;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Data components for the Cartographyr-skill sign/map mechanic (design doc Section 9.1). No
 * special items anymore, 2026-09-25 (see decisions.md) -- signs/maps ride on genuinely vanilla
 * items now, see {@code knowledge.SignListener} for the actual placement/reading behavior.
 */
public final class ModItems {

    private ModItems() {
    }

    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, LyfeMod.MODID);

    // Persisted (survives save/load, e.g. the map sitting in an item frame across a restart) and
    // network-synchronized (so the client can render the item's display name/tooltip correctly).
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<KnowledgeReference>> KNOWLEDGE_REFERENCE =
            DATA_COMPONENTS.registerComponentType("knowledge_reference", builder -> builder
                    .persistent(KnowledgeReference.CODEC)
                    .networkSynchronized(KnowledgeReference.STREAM_CODEC));

    // The Quality score (design doc Section 19.2.1) -- stored on a crafted item stack, 10-130(+)%.
    // Persisted and synced so the item's colored/named display is correct on every client.
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Float>> QUALITY =
            DATA_COMPONENTS.registerComponentType("quality", builder -> builder
                    .persistent(Codec.FLOAT)
                    .networkSynchronized(ByteBufCodecs.FLOAT));

    // Which recipe a Research Notes item is tied to (2026-10-03, user request) -- persisted (a note
    // sitting in a chest/inventory across a restart) and network-synchronized (the dynamic "Research
    // Notes - <Item> (Tier #)" display name must render correctly on every client).
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ResearchNoteTarget>> RESEARCH_NOTE_TARGET =
            DATA_COMPONENTS.registerComponentType("research_note_target", builder -> builder
                    .persistent(ResearchNoteTarget.CODEC)
                    .networkSynchronized(ResearchNoteTarget.STREAM_CODEC));

    // Which cooking recipe a "Recipe Notes" item is tied to (design doc, 2026-10-03 cooking
    // overhaul) -- kept as its own component/item (not reusing RESEARCH_NOTE_TARGET/RESEARCH_NOTES)
    // so Recipe Notes have a separate dynamic name/icon from equipment Research Notes.
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<RecipeNoteTarget>> RECIPE_NOTE_TARGET =
            DATA_COMPONENTS.registerComponentType("recipe_note_target", builder -> builder
                    .persistent(RecipeNoteTarget.CODEC)
                    .networkSynchronized(RecipeNoteTarget.STREAM_CODEC));

    // Fisherman skill (design doc, 2026-10-03 user request) -- see fishing.* classes.
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<FishWeight>> FISH_WEIGHT =
            DATA_COMPONENTS.registerComponentType("fish_weight", builder -> builder
                    .persistent(FishWeight.CODEC)
                    .networkSynchronized(FishWeight.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CatchBagContents>> CATCH_BAG_CONTENTS =
            DATA_COMPONENTS.registerComponentType("catch_bag_contents", builder -> builder
                    .persistent(CatchBagContents.CODEC)
                    .networkSynchronized(CatchBagContents.STREAM_CODEC));

    // Shared by SunkenTreasureItem and every LockedChestItem variant -- same shape ("a baked-in list
    // of ItemStacks, popped one at a time"), one component type is enough for both.
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<FixedLootContents>> FIXED_LOOT_CONTENTS =
            DATA_COMPONENTS.registerComponentType("fixed_loot_contents", builder -> builder
                    .persistent(FixedLootContents.CODEC)
                    .networkSynchronized(FixedLootContents.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> LOCKED_CHEST_LOCKED =
            DATA_COMPONENTS.registerComponentType("locked_chest_locked", builder -> builder
                    .persistent(Codec.BOOL)
                    .networkSynchronized(ByteBufCodecs.BOOL));

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(LyfeMod.MODID);

    // Lyfe's first real custom Item -- every target/tier is baked into RESEARCH_NOTE_TARGET above,
    // never into a separate registered item per recipe.
    public static final DeferredItem<ResearchNoteItem> RESEARCH_NOTES = ITEMS.register(
            "research_notes",
            id -> new ResearchNoteItem(new Item.Properties()
                    .stacksTo(16)
                    .setId(ResourceKey.create(Registries.ITEM, id))));

    public static final DeferredItem<RecipeNoteItem> RECIPE_NOTES = ITEMS.register(
            "recipe_notes",
            id -> new RecipeNoteItem(new Item.Properties()
                    .stacksTo(16)
                    .setId(ResourceKey.create(Registries.ITEM, id))));

    // Fish cleaning output (design doc, 2026-10-03 user request) -- a plain raw ingredient item.
    // COOKED_FISH_MEAT and its furnace/smoker/campfire recipes were removed the same day once the
    // cooking overhaul began: "my unified cooking system will be the only cooking system" -- Fish
    // Meat now feeds into cook.FoodMaterialPool as a Tier 1 ingredient instead.
    public static final DeferredItem<Item> FISH_MEAT = ITEMS.register(
            "fish_meat",
            id -> new Item(new Item.Properties()
                    .food(new FoodProperties.Builder().nutrition(2).saturationModifier(0.1F).build())
                    .setId(ResourceKey.create(Registries.ITEM, id))));

    public static final DeferredItem<CatchBagItem> CATCH_BAG = ITEMS.register(
            "catch_bag",
            id -> new CatchBagItem(new Item.Properties()
                    .stacksTo(1)
                    .setId(ResourceKey.create(Registries.ITEM, id))));

    public static final DeferredItem<SunkenTreasureItem> SUNKEN_TREASURE = ITEMS.register(
            "sunken_treasure_bag",
            id -> new SunkenTreasureItem(new Item.Properties()
                    .stacksTo(1)
                    .setId(ResourceKey.create(Registries.ITEM, id))));

    private static final Map<String, DeferredItem<LockedChestItem>> LOCKED_CHESTS = new HashMap<>();
    private static final Map<String, DeferredItem<KeyItem>> KEYS = new HashMap<>();

    static {
        for (String variant : FishingConstants.CHEST_VARIANTS) {
            LOCKED_CHESTS.put(variant, ITEMS.register(
                    variant + "_chest",
                    id -> new LockedChestItem(variant, new Item.Properties()
                            .stacksTo(1)
                            .setId(ResourceKey.create(Registries.ITEM, id)))));
            KEYS.put(variant, ITEMS.register(
                    variant + "_key",
                    id -> new KeyItem(variant, new Item.Properties()
                            .setId(ResourceKey.create(Registries.ITEM, id)))));
        }
    }

    public static DeferredItem<LockedChestItem> lockedChestFor(String variant) {
        return LOCKED_CHESTS.get(variant);
    }

    public static DeferredItem<KeyItem> keyFor(String variant) {
        return KEYS.get(variant);
    }
}
