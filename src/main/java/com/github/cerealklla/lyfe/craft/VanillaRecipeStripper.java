package com.github.cerealklla.lyfe.craft;

import com.google.gson.JsonElement;

import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.ModifyRecipeJsonsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

/**
 * Two server-lifecycle hooks for the crafting overhaul (design doc Section 19.4):
 *
 * <ul>
 *   <li>{@link #onServerStarting} loads (or, on first boot, generates) this server's randomized
 *   equipment recipe set via {@link ServerRecipeStore}.</li>
 *   <li>{@link #onModifyRecipeJsons} removes vanilla's own Stone/Iron/Gold/Diamond shaped tool
 *   recipes and Netherite's smithing-table upgrade recipes, so players can't bypass the new
 *   research-gated system via a plain vanilla crafting table. Confirmed against the decompiled
 *   source as the *only* real removal mechanism: {@code RecipeManager}/{@code RecipeMap} have no
 *   runtime mutator at all (a full field reassignment on every reload), so removal has to happen
 *   here, inside the JSON-modification step that runs before every reload's own deserialization --
 *   re-applied identically every time, immune to the "wiped on next reload" problem a one-time
 *   runtime poke would have. No replacement recipe is added here: Lyfe's own crafting-structure
 *   Menu (Section 19.6) doesn't go through {@code RecipeManager} matching at all.</li>
 *   <li>The same method also tags vanilla's own Bundle recipe with a shared {@code "group"}
 *   (2026-10-03, user request) so the Catch Bag (which ships its own recipe,
 *   {@code data/lyfe/recipe/catch_bag.json}, with the identical shape/ingredients and the same
 *   group) shows up as a cyclable alternative in the crafting recipe book -- confirmed against the
 *   decompiled {@code ClientRecipeBook#categorizeAndGroupRecipes} source that this {@code "group"}
 *   string is exactly the real mechanism vanilla's own per-wood-type boat recipes use for that same
 *   "click to cycle between variants" UI, not anything bespoke to boats. Vanilla's shipped
 *   {@code bundle.json} has no group of its own, so it's added here rather than overwritten via a
 *   conflicting datapack file at the same path.</li>
 * </ul>
 */
public final class VanillaRecipeStripper {

    private static final Identifier BUNDLE_RECIPE_ID = Identifier.withDefaultNamespace("bundle");

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        ServerRecipeStore.loadOrGenerate(event.getServer());
    }

    @SubscribeEvent
    public void onModifyRecipeJsons(ModifyRecipeJsonsEvent event) {
        for (EquipmentTierLadder.ToolType tool : EquipmentTierLadder.ToolType.values()) {
            // Stone/Iron/Gold/Diamond (Tiers 1-4): plain minecraft:crafting_shaped recipes, whose
            // id matches their result item name exactly (confirmed against the decompiled
            // datapack JSON, e.g. "iron_sword.json" produces "minecraft:iron_sword").
            for (int tier = EquipmentTierLadder.MIN_TIER; tier < EquipmentTierLadder.MAX_TIER; tier++) {
                event.getRecipeJsons().remove(EquipmentTierLadder.itemId(tool, tier));
            }
            // Netherite (Tier 5): not a shaped recipe at all -- a minecraft:smithing_transform
            // upgrading an existing Diamond item, under its own differently-named recipe id.
            event.getRecipeJsons().remove(smithingRecipeId(tool));
        }

        // Armor (added 2026-10-04, see decisions.md) -- same two-part removal. Chainmail (Tier 1)
        // has no real vanilla recipe at all (confirmed against the decompiled datapack), so its
        // removal call below is a harmless no-op, not a bug.
        for (net.minecraft.world.item.equipment.ArmorType armorType : EquipmentTierLadder.ARMOR_TYPES) {
            for (int tier = EquipmentTierLadder.MIN_TIER; tier < EquipmentTierLadder.MAX_TIER; tier++) {
                event.getRecipeJsons().remove(EquipmentTierLadder.armorItemId(armorType, tier));
            }
            event.getRecipeJsons().remove(smithingArmorRecipeId(armorType));
        }

        JsonElement bundleJson = event.getRecipeJsons().get(BUNDLE_RECIPE_ID);
        if (bundleJson != null && bundleJson.isJsonObject()) {
            bundleJson.getAsJsonObject().addProperty("group", "bundle");
        }
    }

    // Netherite's real recipe id is "netherite_sword_smithing" (confirmed against the decompiled
    // datapack JSON) -- a materially different vanilla mechanic from the shaped recipes above, but
    // still just another JSON entry to strip from the same map.

    private static Identifier smithingRecipeId(EquipmentTierLadder.ToolType tool) {
        return Identifier.withDefaultNamespace("netherite_" + tool.name().toLowerCase(java.util.Locale.ROOT) + "_smithing");
    }

    // Same "netherite_<piece>_smithing" convention as tools (e.g. "netherite_helmet_smithing").
    private static Identifier smithingArmorRecipeId(net.minecraft.world.item.equipment.ArmorType armorType) {
        return Identifier.withDefaultNamespace("netherite_" + armorType.getName() + "_smithing");
    }
}
