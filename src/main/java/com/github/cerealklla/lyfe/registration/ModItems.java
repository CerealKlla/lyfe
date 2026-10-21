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
import com.github.cerealklla.lyfe.craft.FocusLadder;
import com.github.cerealklla.lyfe.craft.NatureStaffLadder;
import com.github.cerealklla.lyfe.magician.FocusData;
import com.github.cerealklla.lyfe.magician.FocusItem;
import com.github.cerealklla.lyfe.magician.NatureStaffItem;
import com.github.cerealklla.lyfe.recallcinite.RecallciniteTotemItem;
import com.github.cerealklla.lyfe.research.ResearchNoteItem;
import com.github.cerealklla.lyfe.research.ResearchNoteTarget;
import com.github.cerealklla.lyfe.socket.DieGrinderItem;
import com.github.cerealklla.lyfe.socket.GemItem;
import com.github.cerealklla.lyfe.socket.GemQuality;
import com.github.cerealklla.lyfe.socket.GemType;
import com.github.cerealklla.lyfe.socket.PryBarItem;
import com.github.cerealklla.lyfe.socket.RuneId;
import com.github.cerealklla.lyfe.socket.RuneItem;
import com.github.cerealklla.lyfe.socket.SocketData;
import com.github.cerealklla.lyfe.socket.SocketToolRegistry;

import com.mojang.serialization.Codec;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemAttributeModifiers;
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

    // The exact (unrounded) icons value baked into a crafted food item (2026-10-10, for
    // Settlemynts' per-quality Shop listings) -- the FOOD component's nutrition is a lossy
    // round(icons * 2.0), and the custom name is just display text, so neither is a reliable
    // source of truth for "is this the exact same quality as that other stack." This is the real
    // one. See cook.CookingListener#bakeIcons.
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Double>> CRAFTED_FOOD_ICONS =
            DATA_COMPONENTS.registerComponentType("crafted_food_icons", builder -> builder
                    .persistent(Codec.DOUBLE)
                    .networkSynchronized(ByteBufCodecs.DOUBLE));

    // The Socketing/Extracting system (explicit user spec, 2026-10-17) -- see socket.SocketData's own
    // doc. Attachable to any weapon/tool/armor item (vanilla or modded), not just the two new tools.
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SocketData>> SOCKET_DATA =
            DATA_COMPONENTS.registerComponentType("socket_data", builder -> builder
                    .persistent(SocketData.CODEC)
                    .networkSynchronized(SocketData.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GemQuality>> GEM_QUALITY =
            DATA_COMPONENTS.registerComponentType("gem_quality", builder -> builder
                    .persistent(GemQuality.CODEC)
                    .networkSynchronized(GemQuality.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<RuneId>> RUNE_ID =
            DATA_COMPONENTS.registerComponentType("rune_id", builder -> builder
                    .persistent(RuneId.CODEC)
                    .networkSynchronized(RuneId.STREAM_CODEC));

    // Which Runeword (by name) is currently active on a socketed stack, if any -- set/cleared by
    // socket.SocketApplier#recompute alongside the real attribute recompute. Needed purely so the
    // CLIENT can show the Runeword's name/description in a tooltip (explicit user mockup,
    // 2026-10-18): the real randomized rune-sequence-to-Runeword mapping
    // (socket.ServerRunewordStore) only ever exists server-side, so the client can't re-derive
    // "which Runeword is this" on its own -- it just reads this already-networked name and looks up
    // the (server/client-identical, baked-in) RunewordCatalog entry by it.
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> ACTIVE_RUNEWORD_NAME =
            DATA_COMPONENTS.registerComponentType("active_runeword_name", builder -> builder
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8));

    // Focus (design doc Section 25) -- the randomly-rolled variant/stats/granted-spell payload,
    // see magician.FocusData's own doc. Persisted and synced for the same reasons SOCKET_DATA is.
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<FocusData>> FOCUS_DATA =
            DATA_COMPONENTS.registerComponentType("focus_data", builder -> builder
                    .persistent(FocusData.CODEC)
                    .networkSynchronized(FocusData.STREAM_CODEC));

    // Summoning Stone (design doc Section 26) -- the bound mount's captured snapshot/cooldown/active-
    // summon state, see summoning.SummoningStoneData's own doc. Persisted and synced (synced so the
    // item's own tooltip/cooldown state renders correctly on the holder's client).
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.github.cerealklla.lyfe.summoning.SummoningStoneData>> SUMMONING_STONE_DATA =
            DATA_COMPONENTS.registerComponentType("summoning_stone_data", builder -> builder
                    .persistent(com.github.cerealklla.lyfe.summoning.SummoningStoneData.CODEC)
                    .networkSynchronized(com.github.cerealklla.lyfe.summoning.SummoningStoneData.STREAM_CODEC));

    // Real bug found 2026-10-21 while adding Focus socketing: socket.SocketApplier#recompute used to
    // wholesale-replace DATA_COMPONENTS.ATTRIBUTE_MODIFIERS from socket contents alone, silently
    // destroying a Nature Staff's/crafted armor's/Focus's own crafted-in stat bonuses the moment any
    // socket changed -- confirmed-wrong by the user ("Socketing was always meant to be supplemental
    // to the standard stats generated from crafting, why would it wipe them out?"). Every craft-time
    // site that sets ATTRIBUTE_MODIFIERS for a custom-stat item now also stashes a copy here, server-
    // only (no client ever needs to read this directly -- the real, merged ATTRIBUTE_MODIFIERS stays
    // fully synced as before), so SocketApplier can always rebuild "base stats + socket stats" instead
    // of "socket stats only." A plain, never-crafted-through-this-system item (a vanilla sword, say)
    // has no stashed value here -- SocketApplier falls back to that item's own registered default.
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ItemAttributeModifiers>> BASE_ATTRIBUTE_MODIFIERS =
            DATA_COMPONENTS.registerComponentType("base_attribute_modifiers", builder -> builder
                    .persistent(ItemAttributeModifiers.CODEC));

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(LyfeMod.MODID);

    // Lyfe's first real custom Item -- every target/tier is baked into RESEARCH_NOTE_TARGET above,
    // never into a separate registered item per recipe.
    public static final DeferredItem<ResearchNoteItem> RESEARCH_NOTES = ITEMS.register(
            "research_notes",
            id -> new ResearchNoteItem(new Item.Properties()
                    .stacksTo(500)
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

    // The Recallcinite Totem (design doc, 2026-10-09 user request) -- undroppable and stacksTo(1),
    // every player granted exactly one automatically (see recallcinite.RecallciniteListener).
    public static final DeferredItem<RecallciniteTotemItem> RECALLCINITE_TOTEM = ITEMS.register(
            "recallcinite_totem",
            id -> new RecallciniteTotemItem(new Item.Properties()
                    .stacksTo(1)
                    .setId(ResourceKey.create(Registries.ITEM, id))));

    // The Socketing/Extracting tools (explicit user spec, 2026-10-17) -- see socket.SocketToolRegistry's
    // own doc for why these are Lyfe's first-ever brand-new custom tool Items (every other
    // crafting-overhaul "tool" is a vanilla item id). Registered under the exact ids
    // SocketToolRegistry#dieGrinderId/#pryBarId expect.
    public static final DeferredItem<DieGrinderItem> DIAMOND_DIE_GRINDER = ITEMS.register(
            "diamond_die_grinder",
            id -> new DieGrinderItem(new Item.Properties()
                    .stacksTo(1)
                    .durability(SocketToolRegistry.BASE_DURABILITY)
                    .setId(ResourceKey.create(Registries.ITEM, id))));

    // Nature Staff, 5 tiers (design doc Section 24) -- see NatureStaffLadder's own doc for why this
    // is a brand-new item family rather than a ToolType/EquipmentTierLadder entry.
    public static final DeferredItem<NatureStaffItem> NATURE_STAFF_T1 = ITEMS.register(
            "nature_staff_t1",
            id -> new NatureStaffItem(1, new Item.Properties()
                    .durability(NatureStaffLadder.material(1).durability())
                    .setId(ResourceKey.create(Registries.ITEM, id))));
    public static final DeferredItem<NatureStaffItem> NATURE_STAFF_T2 = ITEMS.register(
            "nature_staff_t2",
            id -> new NatureStaffItem(2, new Item.Properties()
                    .durability(NatureStaffLadder.material(2).durability())
                    .setId(ResourceKey.create(Registries.ITEM, id))));
    public static final DeferredItem<NatureStaffItem> NATURE_STAFF_T3 = ITEMS.register(
            "nature_staff_t3",
            id -> new NatureStaffItem(3, new Item.Properties()
                    .durability(NatureStaffLadder.material(3).durability())
                    .setId(ResourceKey.create(Registries.ITEM, id))));
    public static final DeferredItem<NatureStaffItem> NATURE_STAFF_T4 = ITEMS.register(
            "nature_staff_t4",
            id -> new NatureStaffItem(4, new Item.Properties()
                    .durability(NatureStaffLadder.material(4).durability())
                    .setId(ResourceKey.create(Registries.ITEM, id))));
    public static final DeferredItem<NatureStaffItem> NATURE_STAFF_T5 = ITEMS.register(
            "nature_staff_t5",
            id -> new NatureStaffItem(5, new Item.Properties()
                    .durability(NatureStaffLadder.material(5).durability())
                    .setId(ResourceKey.create(Registries.ITEM, id))));

    // Focus, 4 tiers (design doc Section 25) -- see craft.FocusLadder's own doc for why this is a
    // brand-new item family. Deliberately no .durability(...) call -- not damageable, see
    // magician.FocusItem's own doc.
    public static final DeferredItem<FocusItem> FOCUS_T2 = ITEMS.register(
            "focus_t2",
            id -> new FocusItem(2, new Item.Properties()
                    .stacksTo(1)
                    .setId(ResourceKey.create(Registries.ITEM, id))));
    public static final DeferredItem<FocusItem> FOCUS_T3 = ITEMS.register(
            "focus_t3",
            id -> new FocusItem(3, new Item.Properties()
                    .stacksTo(1)
                    .setId(ResourceKey.create(Registries.ITEM, id))));
    public static final DeferredItem<FocusItem> FOCUS_T4 = ITEMS.register(
            "focus_t4",
            id -> new FocusItem(4, new Item.Properties()
                    .stacksTo(1)
                    .setId(ResourceKey.create(Registries.ITEM, id))));
    public static final DeferredItem<FocusItem> FOCUS_T5 = ITEMS.register(
            "focus_t5",
            id -> new FocusItem(5, new Item.Properties()
                    .stacksTo(1)
                    .setId(ResourceKey.create(Registries.ITEM, id))));

    // Summoning Stone (design doc Section 26) -- stacksTo(1), same reasoning as Research Notes/Focus:
    // every stone carries genuinely unique per-instance data (a bound mount's own saved state), and
    // two stones should never silently merge regardless of whether their NBT happens to coincide.
    public static final DeferredItem<com.github.cerealklla.lyfe.summoning.SummoningStoneItem> SUMMONING_STONE = ITEMS.register(
            "summoning_stone",
            id -> new com.github.cerealklla.lyfe.summoning.SummoningStoneItem(new Item.Properties()
                    .stacksTo(1)
                    .setId(ResourceKey.create(Registries.ITEM, id))));

    public static final DeferredItem<PryBarItem> DIAMOND_PRY_BAR = ITEMS.register(
            "diamond_pry_bar",
            id -> new PryBarItem(new Item.Properties()
                    .stacksTo(1)
                    .durability(SocketToolRegistry.BASE_DURABILITY)
                    .setId(ResourceKey.create(Registries.ITEM, id))));

    // 6 Gem items (one per GemType -- quality is a stored component, not a separate item, see
    // socket.GemItem's own doc).
    private static final Map<GemType, DeferredItem<GemItem>> GEMS = new HashMap<>();

    static {
        for (GemType type : GemType.values()) {
            GEMS.put(type, ITEMS.register(
                    "gem_" + type.name().toLowerCase(java.util.Locale.ROOT),
                    id -> new GemItem(type, new Item.Properties()
                            .setId(ResourceKey.create(Registries.ITEM, id)))));
        }
    }

    public static DeferredItem<GemItem> gemFor(GemType type) {
        return GEMS.get(type);
    }

    // One shared Rune item for all 30 RuneIds -- see socket.RuneItem's own doc.
    public static final DeferredItem<RuneItem> RUNE = ITEMS.register(
            "rune",
            id -> new RuneItem(new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, id))));
}
