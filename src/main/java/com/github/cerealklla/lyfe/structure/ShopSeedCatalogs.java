package com.github.cerealklla.lyfe.structure;

import java.util.ArrayList;
import java.util.List;

import com.github.cerealklla.lyfe.cook.FoodTierLadder;
import com.github.cerealklla.lyfe.cook.RecipeNoteItem;
import com.github.cerealklla.lyfe.craft.EquipmentTierLadder;
import com.github.cerealklla.lyfe.craft.NatureStaffLadder;
import com.github.cerealklla.lyfe.research.ResearchNoteItem;
import com.github.cerealklla.settlemynts.api.Settlemynts;
import com.github.cerealklla.settlemynts.zone.SeedListing;
import com.github.cerealklla.settlemynts.zone.ShopResource;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.ArmorType;

/**
 * Registers Shop Seed Catalogs for the four Zone Types tied to this mod's own equipment/food/
 * research systems -- Armorer, Blacksmith, Grocer, Restaurant. The equivalent for Lumberyard/
 * Building Supplier/Stonemason lives in Blueprynts' own {@code bridge.ShopSeedCatalogs} instead,
 * since those are plain vanilla goods needing no Lyfe knowledge at all. This class must only ever be
 * referenced from behind a {@code ModList.get().isLoaded("settlemynts")} check at the call site (see
 * {@code LyfeMod}'s own registration).
 *
 * <p><b>Listing-registration only -- never auto-stocked, 2026-10-06 correction</b> -- a prior pass
 * had every physical-good entry here (armor/sword/raw-ingredient/finished-meal) auto-deposited into
 * the shop's own boxes, none of which was ever actually asked for. Real user correction, then
 * clarification the same day: "I said gold and research notes/recipes get restocked overnight,
 * nothing else... the plot would have to buy from other plots/settlements if it needed such
 * resources" followed by "what I meant [by Seed] was configure the shop... so a player doesn't have
 * to set up every single item for every shop." So every entry below still registers a real
 * item+price listing (the actual point of a catalog), but {@code zone.ShopSeeding#applyCatalog}
 * now enforces generically that a resource-backed {@link SeedListing} is never auto-deposited --
 * nothing here needs to special-case that. A listing with empty stock just shows "Out of stock"
 * until an owner, an NPC worker, or a trade from another plot actually puts goods in the boxes.
 *
 * <p><b>Tier gating</b>: every entry here is capped at {@code min(plotTier,
 * EquipmentTierLadder.MAX_REACHABLE_TIER)} for equipment, or {@code min(plotTier,
 * FoodTierLadder.MAX_TIER)} for food -- "the shop can only sell up to the Tier the plot itself is
 * at" (explicit user request).
 *
 * <p><b>Research/Recipe Notes</b>: real, tangible {@link ItemStack}s minted via {@code
 * ResearchNoteItem.createFor}/{@code RecipeNoteItem.createFor} -- these carry embedded component
 * data a bare {@link ShopResource} can't represent, so they're seeded via {@link
 * SeedListing#ofStacks}, one combined listing per Zone Type bundling every eligible tier's notes
 * under one flat price (a flagged simplification -- see this feature's own plan file/decisions.md:
 * {@code ShopResource} matches by plain item id, so two notes sharing that id can't carry two
 * different listed prices under the current Shop model). Unlike the physical-good listings above,
 * these DO get deposited (and re-topped-up nightly, see {@code zone.ShopSeeding#restockAtMidnight})
 * -- a Note has no other possible source, nothing else in the game can ever produce one.
 *
 * <p><b>Checklist for every new weapon/tool/armor family (added 2026-10-21, real bug: Nature Staff
 * shipped fully craftable/researchable but was never added here, so no Blacksmith plot of any age
 * could ever list or auto-craft it)</b>: a plain new {@code EquipmentTierLadder.ToolType}/{@code
 * ArmorType} enum member needs nothing extra -- {@link #blacksmithListings}/{@link
 * #armorerListings} already loop those generically. A wholly new family outside those enums (its
 * own small ladder class, like {@code craft.NatureStaffLadder} or the Bow/Crossbow fixed-tier
 * entries) must be added here by hand, in whichever of these functions matches it -- this is NOT
 * optional, unlike the Research Note/passive-research/skill-gate integrations those classes'
 * own docs separately flag as deferrable: {@code zone.ShopSeeding}'s Planned Inventory targets are
 * entirely catalog-sourced, so skipping this step means an NPC-owned plot can never buy/sell/
 * auto-craft the item at all, no matter how complete its recipe/crafting-structure support is.
 */
public final class ShopSeedCatalogs {

    private static final int ARMOR_PRICE_PER_TIER = 10;
    private static final int SWORD_PRICE_PER_TIER = 8;
    private static final int EQUIPMENT_STOCK = 4;
    private static final int NOTE_PRICE = 50;
    private static final int MEAL_PRICE_PER_TIER = 3;
    private static final int MEAL_STOCK = 8;

    private ShopSeedCatalogs() {
    }

    public static void registerAll() {
        Settlemynts.registerShopSeedCatalog(Identifier.fromNamespaceAndPath("blueprynts", "armorer"),
                tieredCatalog(plotTier -> armorerListings(cap(plotTier, EquipmentTierLadder.MAX_REACHABLE_TIER))));
        Settlemynts.registerShopSeedCatalog(Identifier.fromNamespaceAndPath("blueprynts", "blacksmith"),
                tieredCatalog(plotTier -> blacksmithListings(cap(plotTier, EquipmentTierLadder.MAX_REACHABLE_TIER))));
        Settlemynts.registerShopSeedCatalog(Identifier.fromNamespaceAndPath("blueprynts", "grocer"),
                (level, plotAnchor, plotTier) -> grocerListings());
        Settlemynts.registerShopSeedCatalog(Identifier.fromNamespaceAndPath("blueprynts", "restaurant"),
                tieredCatalog(plotTier -> restaurantListings(cap(plotTier, FoodTierLadder.MAX_TIER))));
    }

    /**
     * Wraps a plot-tier-only listing function as a {@code hasTierProgression() == true} catalog --
     * 2026-10-10, see {@code ShopSeedCatalog#hasTierProgression}'s own doc. A plain lambda can't
     * override a default method, so this is a real anonymous class instead for just these three
     * (genuinely tiered) catalogs.
     */
    private static com.github.cerealklla.settlemynts.zone.ShopSeedCatalog tieredCatalog(java.util.function.IntFunction<List<SeedListing>> listingsForTier) {
        return new com.github.cerealklla.settlemynts.zone.ShopSeedCatalog() {
            @Override
            public List<SeedListing> seedListingsFor(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos plotAnchor, int plotTier) {
                return listingsForTier.apply(plotTier);
            }

            @Override
            public boolean hasTierProgression() {
                return true;
            }
        };
    }

    private static int cap(int plotTier, int max) {
        return Math.max(1, Math.min(plotTier, max));
    }

    private static List<SeedListing> armorerListings(int maxTier) {
        List<SeedListing> listings = new ArrayList<>();
        List<ItemStack> notes = new ArrayList<>();
        // Starts at Tier 0 (Leather) -- real report, 2026-10-16: "tier 1 armorer should also list
        // leather armor alongside chain." The loop used to start at 1 (Chainmail), silently skipping
        // Leather at every Plot Tier including 1, even though Tier 0 armor is a real, auto-known rung
        // on EquipmentTierLadder's own ladder (same "known by default but still sellable" treatment
        // Wood already gets on the tool side).
        for (int tier = 0; tier <= maxTier; tier++) {
            // Math.max(1, tier) -- ARMOR_PRICE_PER_TIER * tier would price Tier 0 (Leather) at 0
            // (free). Priced the same as Tier 1 (Chainmail) rather than inventing a new, smaller
            // fractional step -- every tier >= 1's own existing price is unchanged.
            int price = ARMOR_PRICE_PER_TIER * Math.max(1, tier);
            for (ArmorType armorType : EquipmentTierLadder.ARMOR_TYPES) {
                Identifier itemId = EquipmentTierLadder.armorItemId(armorType, tier);
                listings.add(SeedListing.ofResource(ShopResource.ofItem(itemId), price, EQUIPMENT_STOCK));
                notes.add(ResearchNoteItem.createFor(itemId, tier));
            }
        }
        if (!notes.isEmpty()) {
            listings.add(SeedListing.ofStacks(NOTE_PRICE, notes));
        }
        return listings;
    }

    private static List<SeedListing> blacksmithListings(int maxTier) {
        List<SeedListing> listings = new ArrayList<>();
        List<ItemStack> notes = new ArrayList<>();
        // Starts at Tier 0 (Wood) -- same real gap and fix as armorerListings' own Leather fix
        // above, confirmed by the user ("I meant tier 0"): the loop used to start at 1 (Stone),
        // silently skipping Wood at every Plot Tier including 1, even though it's a real, auto-known
        // rung on the ladder.
        //
        // Widened 2026-10-16 (explicit follow-up: "I'd like blacksmith to list weapons and tools
        // too") from Sword-only to every EquipmentTierLadder.ToolType -- Pickaxe/Axe/Shovel/Hoe are
        // real tools and Spear is a real weapon, none of which this catalog ever listed before,
        // despite the Blacksmith Zone Type already being the sole target for both the "Toolsmith" and
        // "Weaponsmith" natural-village professions (see zone.NaturalVillagePlotGenerator).
        for (int tier = 0; tier <= maxTier; tier++) {
            // Math.max(1, tier) -- see armorerListings' own identical fix; avoids a free (price 0)
            // Tier 0 listing without changing any existing tier >= 1's price.
            int price = SWORD_PRICE_PER_TIER * Math.max(1, tier);
            for (EquipmentTierLadder.ToolType toolType : EquipmentTierLadder.ToolType.values()) {
                Identifier itemId = EquipmentTierLadder.itemId(toolType, tier);
                listings.add(SeedListing.ofResource(ShopResource.ofItem(itemId), price, EQUIPMENT_STOCK));
                notes.add(ResearchNoteItem.createFor(itemId, tier));
            }
        }
        // Bow/Crossbow (added 2026-10-08, see EquipmentTierLadder's own doc) are real weapons but
        // don't fit the tier-ladder loop above -- each is pinned to exactly one fixed tier, no
        // Wood/Stone/... family. Listed once each, only once the plot's own Tier reaches theirs.
        for (Identifier rangedWeaponId : EquipmentTierLadder.allGeneratedRangedWeaponIds()) {
            int weaponTier = EquipmentTierLadder.rangedWeaponTier(rangedWeaponId);
            if (weaponTier > maxTier) {
                continue;
            }
            listings.add(SeedListing.ofResource(ShopResource.ofItem(rangedWeaponId),
                    SWORD_PRICE_PER_TIER * Math.max(1, weaponTier), EQUIPMENT_STOCK));
        }
        // Nature Staff (added 2026-10-21, real report: "not seeing my blacksmith listing nature
        // staff after upgrading the plot and crafting station") -- root cause confirmed by direct
        // investigation: this method never referenced NatureStaffLadder at all, so no Blacksmith
        // plot of any age was ever going to list/auto-craft it, same shape as the ranged-weapon loop
        // above (its own small ladder, not an EquipmentTierLadder.ToolType, so it can't fall out of
        // that loop automatically). Priced the same as a sword at the matching tier.
        for (int tier = NatureStaffLadder.MIN_TIER; tier <= Math.min(maxTier, NatureStaffLadder.MAX_TIER); tier++) {
            Identifier itemId = NatureStaffLadder.itemId(tier);
            listings.add(SeedListing.ofResource(ShopResource.ofItem(itemId), SWORD_PRICE_PER_TIER * Math.max(1, tier), EQUIPMENT_STOCK));
            notes.add(ResearchNoteItem.createFor(itemId, tier));
        }
        // Focus (added 2026-10-21, design doc Section 25) -- same "own small ladder, needs its own
        // explicit loop" shape as Nature Staff just above (its own small ladder, not an
        // EquipmentTierLadder.ToolType, so it never falls out of the main loop at the top of this
        // method automatically).
        for (int tier = com.github.cerealklla.lyfe.craft.FocusLadder.MIN_TIER; tier <= Math.min(maxTier, com.github.cerealklla.lyfe.craft.FocusLadder.MAX_TIER); tier++) {
            Identifier itemId = com.github.cerealklla.lyfe.craft.FocusLadder.itemId(tier);
            listings.add(SeedListing.ofResource(ShopResource.ofItem(itemId), SWORD_PRICE_PER_TIER * Math.max(1, tier), EQUIPMENT_STOCK));
            notes.add(ResearchNoteItem.createFor(itemId, tier));
        }
        if (!notes.isEmpty()) {
            listings.add(SeedListing.ofStacks(NOTE_PRICE, notes));
        }
        return listings;
    }

    /**
     * Every raw cooking ingredient, derived live from the real recipe graph (2026-10-10, explicit
     * user request: "Grocer's by default should want to buy/sell any food related components, but
     * not final product foods") -- replaces the old hand-picked 8-item list, which was never actually
     * complete (e.g. never included cocoa beans, mushrooms, a bowl, or anything Track B's randomly-
     * generated recipes happen to call for this server). A "component" here is any item referenced in
     * any known food recipe's ingredient map ({@code cook.ServerFoodRecipeStore#all}, Track A's fixed
     * vanilla recipes plus Track B's server-rolled signature dishes alike) that is *not itself* the
     * result of some other food recipe -- that second condition is what keeps a dish that's also an
     * ingredient of a higher-tier one (e.g. Cooked Rabbit feeding into Rabbit Stew) correctly excluded
     * as a "final product food," even though it technically appears in another recipe's component map.
     * Computed fresh on every call, not cached statically -- Track B's recipes are only known once
     * {@code ServerFoodRecipeStore#loadOrGenerate} has actually run for this server, which hasn't
     * happened yet at class-load time.
     *
     * <p>Price is derived from the item's own real vanilla nutrition value when it has one (raw
     * ingredients like Carrot/Potato/Beef genuinely differ in how "valuable" they feel), floored at 1
     * for anything non-edible on its own (Cocoa Beans, a Bowl, Gold Nuggets) -- a reasonable default,
     * not a precisely-tuned economy, same "flagged as tunable" convention every other catalog price
     * in this file already follows. Stock target is the item's own real vanilla max stack size (2026-
     * 10-10, explicit user request: "the grocer should want to keep an entire stack of stock on
     * hand") -- 64 for most of these, but genuinely smaller for whatever doesn't stack that high (e.g.
     * Eggs only stack to 16), read live via {@code ItemStack#getMaxStackSize()} rather than a single
     * flat number. No Tier gating -- ingredients aren't on either tier ladder.
     */
    // Real report, 2026-10-20 (screenshot: "listing some crazy items for sale that don't appear to
    // have valid baseline prices") -- initially misdiagnosed as "a Grocer should never sell gold" and
    // fixed by excluding Gold Nugget/Ingot/Block outright. Corrected by the user the same day: it's
    // entirely correct for a Grocer to sell whatever its real recipes genuinely require (Track B's
    // "signature dish" recipes, e.g. Shepherd's Platter, legitimately use gold as a flavor/rarity
    // ingredient) -- the actual bug was only ever that the *price* was wrong (a flat floor price
    // instead of gold's real value), not which items got listed. See Yconomics' own decisions.md,
    // same date, for the real root cause (derived pricing had never actually been computed on this
    // world at all) and its fix (automatic recalculation every boot, no exclusion needed here).
    private static List<SeedListing> grocerListings() {
        java.util.Set<Identifier> resultIds = new java.util.HashSet<>(FoodTierLadder.allResultIds());
        java.util.Set<Identifier> components = new java.util.LinkedHashSet<>();
        for (com.github.cerealklla.lyfe.cook.GeneratedFoodRecipe recipe : com.github.cerealklla.lyfe.cook.ServerFoodRecipeStore.all().values()) {
            components.addAll(recipe.components().keySet());
        }
        components.removeAll(resultIds);

        List<SeedListing> listings = new ArrayList<>();
        for (Identifier itemId : components) {
            ItemStack sample = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(itemId));
            listings.add(SeedListing.ofResource(ShopResource.ofItem(itemId), grocerPriceFor(sample), sample.getMaxStackSize()));
        }
        return listings;
    }

    private static int grocerPriceFor(ItemStack sample) {
        net.minecraft.world.food.FoodProperties food = sample.get(net.minecraft.core.component.DataComponents.FOOD);
        return food != null ? Math.max(1, food.nutrition()) : 1;
    }

    private static List<SeedListing> restaurantListings(int maxTier) {
        List<SeedListing> listings = new ArrayList<>();
        List<ItemStack> notes = new ArrayList<>();
        for (int tier = 1; tier <= maxTier; tier++) {
            for (Identifier mealId : FoodTierLadder.trackA(tier)) {
                listings.add(SeedListing.ofResource(ShopResource.ofItem(mealId), MEAL_PRICE_PER_TIER * tier, MEAL_STOCK));
            }
            Identifier dishId = FoodTierLadder.trackB(tier);
            listings.add(SeedListing.ofResource(ShopResource.ofItem(dishId), MEAL_PRICE_PER_TIER * tier, MEAL_STOCK));
            notes.add(RecipeNoteItem.createFor(dishId, tier));
        }
        if (!notes.isEmpty()) {
            listings.add(SeedListing.ofStacks(NOTE_PRICE, notes));
        }
        return listings;
    }
}
