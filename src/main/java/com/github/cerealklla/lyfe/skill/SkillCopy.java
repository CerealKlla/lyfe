package com.github.cerealklla.lyfe.skill;

import java.util.Map;

import com.github.cerealklla.lyfe.registration.ModBlocks;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Display-only copy for the Skills screen (design doc Section 13, built as a custom screen per the
 * user's 2026-10-02 decision not to piggyback on vanilla Advancements): a representative vanilla
 * item icon, a short "what grants XP" source line, and a one-line summary per skill. Purely
 * presentational -- none of this feeds back into any gameplay mechanic.
 */
public record SkillCopy(Item icon, String xpSource, String summary) {

    private static final Map<SkillId, SkillCopy> COPY = Map.ofEntries(
            Map.entry(Skills.LUMBERJACK_ID, new SkillCopy(Items.IRON_AXE,
                    "Breaking log blocks",
                    "Faster, more plentiful wood gathering -- including a chance to clear a whole tree in one hit.")),
            Map.entry(Skills.MINER_ID, new SkillCopy(Items.IRON_PICKAXE,
                    "Breaking ore blocks",
                    "Faster, more plentiful ore gathering -- including a chance to clear a whole vein in one hit.")),
            Map.entry(Skills.SURVIVALIST_ID, new SkillCopy(Items.COOKED_BEEF,
                    "Eating food, restoring true hunger",
                    "Raises your true maximum hunger, letting you store more food before healing stops.")),
            Map.entry(Skills.COOK_ID, new SkillCopy(Items.CAMPFIRE,
                    "Cooking food at a Cooking Station (Tier 1-5)",
                    "Crafted meals restore more hunger icons the higher your level and structure tier -- a lot easier to research than equipment recipes.")),
            Map.entry(Skills.CARTOGRAPHYR_ID, new SkillCopy(Items.MAP,
                    "Writing or reading a bound sign/map",
                    "Lets you embed more precise location information into signs and maps you write.")),
            Map.entry(Skills.HISTORIAN_ID, new SkillCopy(Items.BOOK,
                    "Learning historical facts (not yet implemented)",
                    "Will unlock NPC lore/dialogue about local history once that mechanic exists.")),
            Map.entry(Skills.MERCHANT_ID, new SkillCopy(Items.GOLD_NUGGET,
                    "Completing a villager trade",
                    "Better buy/sell prices with villagers and a higher auto-granted Coin Purse tier.")),
            Map.entry(Skills.REINCARNATION_ID, new SkillCopy(Items.TOTEM_OF_UNDYING,
                    "Dying",
                    "Protects more of your inventory/equipment slots from dropping on death.")),
            Map.entry(Skills.SWIMMER_ID, new SkillCopy(Items.TROPICAL_FISH,
                    "Bank XP by consuming air underwater, gain XP when surfacing safely",
                    "More max air, faster air recovery, and faster swimming.")),
            Map.entry(Skills.RESEARCHER_ID, new SkillCopy(ModBlocks.RESEARCH_BENCH_ITEM.get(),
                    "Researching an item at the Research Bench",
                    "A better chance to save the item's durability, fewer critical failures, and more critical successes.")),
            Map.entry(Skills.CRAFTER_ID, new SkillCopy(ModBlocks.CRAFTING_STATION_ITEM.get(),
                    "Crafting at a Crafting Structure",
                    "A chance to save crafting components, higher-Quality results, and access to higher structure tiers.")),
            Map.entry(Skills.HEARTINESS_ID, new SkillCopy(Items.GOLDEN_APPLE,
                    "Recovering health, especially after a mob hit you",
                    "Raises your max health up to 60 (30 hearts), and past level 25 gives a chance to rapidly heal after taking damage.")),
            Map.entry(Skills.FISHERMAN_ID, new SkillCopy(Items.FISHING_ROD,
                    "Catching fish",
                    "Increases bonus-loot chance on every catch, and past level 30 unlocks a chance at Sunken Treasure in deep water.")),
            Map.entry(Skills.FARMER_ID, new SkillCopy(Items.WHEAT,
                    "Harvesting a fully-grown crop",
                    "A chance at a bonus seed, boosted rare leaf drops with a Hoe, a chance to double a whole harvest, and a chance bone meal isn't consumed.")),
            Map.entry(Skills.SWORDSMAN_ID, new SkillCopy(Items.IRON_SWORD,
                    "Hitting a living entity with a Sword",
                    "Raises your unlocked Sword tier -- using a higher tier than you've unlocked costs extra durability.")),
            Map.entry(Skills.AXEMAN_ID, new SkillCopy(Items.IRON_AXE,
                    "Hitting a living entity with an Axe",
                    "Raises your unlocked Axe tier for combat -- separate from Lumberjack's own gathering tier. Using a higher tier than you've unlocked costs extra durability.")),
            Map.entry(Skills.PIKEMAN_ID, new SkillCopy(Items.IRON_SPEAR,
                    "Hitting a living entity with a Spear",
                    "Raises your unlocked Spear tier -- using a higher tier than you've unlocked costs extra durability.")),
            Map.entry(Skills.EXCAVATOR_ID, new SkillCopy(Items.IRON_SHOVEL,
                    "Breaking a shovel-appropriate block with a Shovel",
                    "Raises your unlocked Shovel tier -- using a higher tier than you've unlocked costs extra durability.")),
            Map.entry(Skills.EXPEDITIONIST_ID, new SkillCopy(Items.COMPASS,
                    "Discovering a new Region or Settlement for the first time (bonus XP for a genuinely new Region)",
                    "Unlocks the minimap (5) and its North indicator (10); increases minimap view radius. A full-screen Map, waypoints, and zoom are planned for a future level.")),
            Map.entry(Skills.MAYOR_ID, new SkillCopy(Items.EMERALD,
                    "A plot in a Settlement you're the Mayor (or a Town Planner) of being upgraded a Tier",
                    "Unlocks higher-Tier Zone Types your settlements can establish -- Town Hall Tier also independently gates this.")),
            Map.entry(Skills.RECALLCRAFT_ID, new SkillCopy(Items.ENDER_PEARL,
                    "Binding or recalling with the Recallcinite Totem",
                    "Reduces the Totem's bind/recall cooldown, up to 20% at max level."))
    );

    public static SkillCopy get(SkillId id) {
        SkillCopy copy = COPY.get(id);
        if (copy == null) {
            throw new IllegalArgumentException("No SkillCopy registered for " + id + " -- add one alongside its SkillDefinition");
        }
        return copy;
    }
}
