package com.github.cerealklla.lyfe.cook;

import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.ModifyRecipeJsonsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

/**
 * Two server-lifecycle hooks for the cooking overhaul (design doc Section 19.3), direct mirror of
 * {@code craft.VanillaRecipeStripper}:
 *
 * <ul>
 *   <li>{@link #onServerStarting} loads/generates the server-scoped food recipe set.</li>
 *   <li>{@link #onModifyRecipeJsons} removes every Track A item's real vanilla recipe -- the plain
 *   crafting-table recipe for Bread/Cookie/Cake/Pie/Soup/Stew/Golden Carrot/Golden Apple/Enchanted
 *   Golden Apple, and the smelting/smoking/campfire-cooking trio for every raw-to-cooked meat/fish
 *   pair -- so the unified cooking system (design doc, 2026-10-03 user request: "my unified cooking
 *   system will be the only cooking system") is the only way to obtain any of them. Removing a
 *   recipe id that doesn't actually exist for a given item (e.g. Bread has no "_from_smoking"
 *   variant) is a harmless no-op, confirmed against {@code ModifyRecipeJsonsEvent#getRecipeJsons}'s
 *   real {@code Map#remove} semantics -- no per-item special-casing needed.</li>
 * </ul>
 */
public final class VanillaFoodRecipeStripper {

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        ServerFoodRecipeStore.loadOrGenerate(event.getServer());
    }

    @SubscribeEvent
    public void onModifyRecipeJsons(ModifyRecipeJsonsEvent event) {
        for (int tier = FoodTierLadder.MIN_TIER; tier <= FoodTierLadder.MAX_TIER; tier++) {
            for (Identifier resultId : FoodTierLadder.trackA(tier)) {
                stripAllCookingVariants(event, resultId);
            }
        }
    }

    private void stripAllCookingVariants(ModifyRecipeJsonsEvent event, Identifier resultId) {
        event.getRecipeJsons().remove(resultId);
        event.getRecipeJsons().remove(suffixed(resultId, "_from_smoking"));
        event.getRecipeJsons().remove(suffixed(resultId, "_from_campfire_cooking"));
    }

    private static Identifier suffixed(Identifier id, String suffix) {
        return Identifier.fromNamespaceAndPath(id.getNamespace(), id.getPath() + suffix);
    }
}
