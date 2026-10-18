package com.github.cerealklla.lyfe.craft;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import com.github.cerealklla.lyfe.socket.SocketToolRegistry;
import com.github.cerealklla.nuclyus.server.PersistedServerStore;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

/**
 * The server-scoped (not world-scoped) generated recipe set (design doc Section 19.4) -- a thin
 * static facade over a {@link PersistedServerStore}, which owns the actual "generate once on first
 * boot, persist to the server's own working directory via Gson+Codec, reuse thereafter" mechanics
 * (pulled out to Nuclyus 2026-10-17, shared with {@code cook.ServerFoodRecipeStore}/{@code
 * socket.ServerRunewordStore}).
 */
public final class ServerRecipeStore {

    private static final PersistedServerStore<Identifier, GeneratedRecipe> STORE =
            new PersistedServerStore<>("lyfe_equipment_recipes.json", GeneratedRecipe.CODEC, GeneratedRecipe::resultId) {
                @Override
                protected Map<Identifier, GeneratedRecipe> generate() {
                    return ServerRecipeStore.generate();
                }
            };

    private ServerRecipeStore() {
    }

    public static Optional<GeneratedRecipe> get(Identifier itemId) {
        return STORE.get(itemId);
    }

    public static Map<Identifier, GeneratedRecipe> all() {
        return STORE.all();
    }

    public static void loadOrGenerate(MinecraftServer server) {
        STORE.loadOrGenerate(server);
    }

    /**
     * Forces a brand-new random generation, overwriting the cached file -- added 2026-10-03 for the
     * {@code /lyfe rerollrecipes} debug command, so tuning {@code MaterialPool}/{@code RecipeGenerator}
     * can be iterated live without manually deleting the server's working-directory JSON and
     * rebooting between every change.
     */
    public static void reroll(MinecraftServer server) {
        STORE.reroll(server);
    }

    static Map<Identifier, GeneratedRecipe> generate() {
        Random random = new Random();
        Map<Identifier, GeneratedRecipe> result = new HashMap<>();
        for (EquipmentTierLadder.ToolType tool : EquipmentTierLadder.ToolType.values()) {
            for (int tier = EquipmentTierLadder.MIN_TIER; tier <= EquipmentTierLadder.MAX_TIER; tier++) {
                Identifier itemId = EquipmentTierLadder.itemId(tool, tier);
                Identifier previousTierItem = tier > 0 ? EquipmentTierLadder.itemId(tool, tier - 1) : null;
                result.put(itemId, RecipeGenerator.generate(itemId, tier, previousTierItem, random));
            }
        }
        // Armor (added 2026-10-04, see decisions.md) -- same generation pipeline, no ToolType at all.
        for (var armorType : EquipmentTierLadder.ARMOR_TYPES) {
            for (int tier = EquipmentTierLadder.MIN_TIER; tier <= EquipmentTierLadder.MAX_TIER; tier++) {
                Identifier itemId = EquipmentTierLadder.armorItemId(armorType, tier);
                Identifier previousTierItem = tier > 0 ? EquipmentTierLadder.armorItemId(armorType, tier - 1) : null;
                result.put(itemId, RecipeGenerator.generate(itemId, tier, previousTierItem, random));
            }
        }
        // Ranged weapons (Bow/Crossbow, added 2026-10-08) -- each a single fixed-tier recipe, not a
        // per-tier family (see EquipmentTierLadder's own doc). No previousTierItem of their own.
        for (Identifier itemId : EquipmentTierLadder.allGeneratedRangedWeaponIds()) {
            int tier = EquipmentTierLadder.rangedWeaponTier(itemId);
            result.put(itemId, RecipeGenerator.generate(itemId, tier, null, random));
        }
        // Socketing tools (Diamond Die Grinder/Pry Bar, added 2026-10-17) -- same fixed-tier, no-
        // upgrade-chain shape as ranged weapons above, plus a guaranteed 3-Diamond base merged on top
        // of the random roll (explicit user spec: "3 diamond base + random other components") -- see
        // socket.SocketToolRegistry's own doc for why RECIPE_TIER (4) only governs the random roll's
        // material weighting, independent of the guaranteed Diamond base.
        for (Identifier itemId : SocketToolRegistry.allIds()) {
            GeneratedRecipe rolled = RecipeGenerator.generate(itemId, SocketToolRegistry.RECIPE_TIER, null, random);
            Map<Identifier, Integer> components = new LinkedHashMap<>(rolled.specificComponents());
            components.merge(SocketToolRegistry.diamondItemId(), SocketToolRegistry.GUARANTEED_DIAMOND_COUNT, Integer::sum);
            result.put(itemId, new GeneratedRecipe(itemId, SocketToolRegistry.RECIPE_TIER, components, rolled.genericComponents()));
        }
        // Fixed, hand-authored, non-equipment conversions (Leaf Litter -> Charcoal, added
        // 2026-10-18) -- see MiscRecipes' own doc for why these live outside the equipment ladder.
        result.putAll(MiscRecipes.generate());
        return result;
    }
}
