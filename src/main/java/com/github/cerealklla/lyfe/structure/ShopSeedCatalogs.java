package com.github.cerealklla.lyfe.structure;

import java.util.ArrayList;
import java.util.List;

import com.github.cerealklla.lyfe.cook.FoodTierLadder;
import com.github.cerealklla.lyfe.cook.RecipeNoteItem;
import com.github.cerealklla.lyfe.craft.EquipmentTierLadder;
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
        for (int tier = 1; tier <= maxTier; tier++) {
            for (ArmorType armorType : EquipmentTierLadder.ARMOR_TYPES) {
                Identifier itemId = EquipmentTierLadder.armorItemId(armorType, tier);
                listings.add(SeedListing.ofResource(ShopResource.ofItem(itemId), ARMOR_PRICE_PER_TIER * tier, EQUIPMENT_STOCK));
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
        for (int tier = 1; tier <= maxTier; tier++) {
            Identifier itemId = EquipmentTierLadder.itemId(EquipmentTierLadder.ToolType.SWORD, tier);
            listings.add(SeedListing.ofResource(ShopResource.ofItem(itemId), SWORD_PRICE_PER_TIER * tier, EQUIPMENT_STOCK));
            notes.add(ResearchNoteItem.createFor(itemId, tier));
        }
        if (!notes.isEmpty()) {
            listings.add(SeedListing.ofStacks(NOTE_PRICE, notes));
        }
        return listings;
    }

    private static final int GROCER_STOCK = 64;

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
     * in this file already follows. No Tier gating -- ingredients aren't on either tier ladder.
     */
    private static List<SeedListing> grocerListings() {
        java.util.Set<Identifier> resultIds = new java.util.HashSet<>(FoodTierLadder.allResultIds());
        java.util.Set<Identifier> components = new java.util.LinkedHashSet<>();
        for (com.github.cerealklla.lyfe.cook.GeneratedFoodRecipe recipe : com.github.cerealklla.lyfe.cook.ServerFoodRecipeStore.all().values()) {
            components.addAll(recipe.components().keySet());
        }
        components.removeAll(resultIds);

        List<SeedListing> listings = new ArrayList<>();
        for (Identifier itemId : components) {
            listings.add(SeedListing.ofResource(ShopResource.ofItem(itemId), grocerPriceFor(itemId), GROCER_STOCK));
        }
        return listings;
    }

    private static int grocerPriceFor(Identifier itemId) {
        net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(itemId);
        net.minecraft.world.food.FoodProperties food = new ItemStack(item).get(net.minecraft.core.component.DataComponents.FOOD);
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
