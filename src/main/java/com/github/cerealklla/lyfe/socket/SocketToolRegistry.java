package com.github.cerealklla.lyfe.socket;

import java.util.List;

import com.github.cerealklla.lyfe.LyfeMod;
import com.github.cerealklla.lyfe.registration.ModItems;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

/**
 * The Diamond Die Grinder/Pry Bar (explicit user spec: "Tier 4 tool... 3 diamond base + random other
 * components... durability base is 1000... repair max is 9 diamonds"). Lyfe's first-ever brand-new
 * custom tool {@link Item}s (every existing crafting-overhaul "tool" is a vanilla item id resolved
 * through {@code craft.EquipmentTierLadder}) -- these two are deliberately kept OUT of that ladder
 * entirely (they have no 7-tier material family, no {@code ToolType}) and instead follow the
 * Bow/Crossbow "single fixed-tier recipe, no upgrade-chain predecessor" precedent from {@code
 * EquipmentTierLadder.RANGED_WEAPON_TIERS}, mirrored here with modded item ids instead of vanilla
 * ones.
 *
 * <p><b>"Tier 4" reconciled against the ladder's own numbering</b> (real conflict found before
 * writing any code: Tier 4 on {@code EquipmentTierLadder} is Gold, not Diamond -- Diamond is Tier 5):
 * {@code RECIPE_TIER} (4) is used ONLY for {@code craft.RecipeGenerator}'s material-weighting roll
 * and {@code EquipmentTierLadder.slotCapacity} -- it governs how expensive/rare the *random* extra
 * components are, nothing else. The guaranteed "3 Diamond base" ingredient is added as a fixed,
 * non-randomized component on top (see {@code craft.ServerRecipeStore#generate}), independent of
 * whatever tier number the random roll is weighted toward -- so "Diamond" in the item's name/recipe
 * and "Tier 4" in its difficulty are both honored literally without contradicting each other. {@code
 * craft.CraftingStructureMenu#attemptCraft}'s Tier-4 base-durability override (900, Gold's own
 * rebalanced value) is explicitly excluded for these two items (see {@link #isSocketTool}) so their
 * own registered 1000 durability isn't silently overwritten.
 */
public final class SocketToolRegistry {

    public static final int RECIPE_TIER = 4;
    public static final int GUARANTEED_DIAMOND_COUNT = 3;
    public static final int BASE_DURABILITY = 1000;

    private SocketToolRegistry() {
    }

    public static Identifier dieGrinderId() {
        return Identifier.fromNamespaceAndPath(LyfeMod.MODID, "diamond_die_grinder");
    }

    public static Identifier pryBarId() {
        return Identifier.fromNamespaceAndPath(LyfeMod.MODID, "diamond_pry_bar");
    }

    public static List<Identifier> allIds() {
        return List.of(dieGrinderId(), pryBarId());
    }

    public static boolean isSocketTool(Identifier itemId) {
        return itemId.equals(dieGrinderId()) || itemId.equals(pryBarId());
    }

    public static boolean isDieGrinder(Item item) {
        return item == ModItems.DIAMOND_DIE_GRINDER.get();
    }

    public static boolean isPryBar(Item item) {
        return item == ModItems.DIAMOND_PRY_BAR.get();
    }

    public static Identifier diamondItemId() {
        return BuiltInRegistries.ITEM.getKey(net.minecraft.world.item.Items.DIAMOND);
    }
}
