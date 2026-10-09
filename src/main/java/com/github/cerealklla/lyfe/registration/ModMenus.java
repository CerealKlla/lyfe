package com.github.cerealklla.lyfe.registration;

import java.util.ArrayList;
import java.util.List;

import com.github.cerealklla.lyfe.LyfeMod;
import com.github.cerealklla.lyfe.cook.CookingStructureMenu;
import com.github.cerealklla.lyfe.cook.GeneratedFoodRecipe;
import com.github.cerealklla.lyfe.craft.CraftingStructureMenu;
import com.github.cerealklla.lyfe.craft.GeneratedRecipe;
import com.github.cerealklla.lyfe.fishing.FishCleaningMenu;
import com.github.cerealklla.lyfe.repair.RepairStructureMenu;
import com.github.cerealklla.lyfe.research.ResearchMenu;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {

    private ModMenus() {
    }

    public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(Registries.MENU, LyfeMod.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<ResearchMenu>> RESEARCH_BENCH = MENU_TYPES.register(
            "research_bench",
            () -> IMenuTypeExtension.create((windowId, inventory, extraData) -> new ResearchMenu(null, windowId, inventory)));

    public static final DeferredHolder<MenuType<?>, MenuType<FishCleaningMenu>> FISH_CLEANING_STATION = MENU_TYPES.register(
            "fish_cleaning_station",
            () -> IMenuTypeExtension.create((windowId, inventory, extraData) -> new FishCleaningMenu(null, windowId, inventory)));

    // Opened by RepairInteractionListener in place of vanilla's own Anvil/Grindstone menu -- no
    // BlockEntity behind this one (both are plain vanilla blocks), so the client-side reconstruction
    // just needs an empty repair slot, same null-backed shape as RESEARCH_BENCH/FISH_CLEANING_STATION
    // above.
    public static final DeferredHolder<MenuType<?>, MenuType<RepairStructureMenu>> REPAIR_STRUCTURE = MENU_TYPES.register(
            "repair_structure",
            () -> IMenuTypeExtension.create((windowId, inventory, extraData) -> new RepairStructureMenu(null, windowId, inventory)));

    // One shared MenuType for every crafting-structure tier -- tier and the known/eligible recipe
    // list both come from CraftingStructureBlockEntity#writeClientSideData's extra-data buffer
    // (fixed 2026-10-02, see decisions.md -- the client previously always assumed Tier 2's slot
    // count, crashing on every other tier).
    public static final DeferredHolder<MenuType<?>, MenuType<CraftingStructureMenu>> CRAFTING_STRUCTURE = MENU_TYPES.register(
            "crafting_structure",
            () -> IMenuTypeExtension.create((windowId, inventory, extraData) -> {
                int tier = extraData.readVarInt();
                int count = extraData.readVarInt();
                List<GeneratedRecipe> craftable = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    craftable.add(GeneratedRecipe.readFrom(extraData));
                }
                // Real Slot y is final once constructed (2026-10-03 fix), so the "shrink to fit"
                // inventory-row position must be known up front -- safe to read the client's real
                // window size here since this factory lambda is only ever invoked client-side (the
                // server never builds a client-mirror Menu), the same precondition that already lets
                // the constructor just below be client-only despite living in this common-package file.
                int windowHeight = net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledHeight();
                int inventoryY = CraftingStructureMenu.computeInventoryY(windowHeight);
                boolean canUpgrade = extraData.readBoolean();
                return new CraftingStructureMenu(null, windowId, inventory, tier, craftable, inventoryY, canUpgrade);
            }));

    // Cooking overhaul (design doc Section 19.3/19.6, 2026-10-03) -- direct mirror of
    // CRAFTING_STRUCTURE's own client factory.
    public static final DeferredHolder<MenuType<?>, MenuType<CookingStructureMenu>> COOKING_STRUCTURE = MENU_TYPES.register(
            "cooking_structure",
            () -> IMenuTypeExtension.create((windowId, inventory, extraData) -> {
                int tier = extraData.readVarInt();
                int count = extraData.readVarInt();
                List<GeneratedFoodRecipe> craftable = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    craftable.add(GeneratedFoodRecipe.readFrom(extraData));
                }
                int windowHeight = net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledHeight();
                int inventoryY = CookingStructureMenu.computeInventoryY(windowHeight);
                boolean canUpgrade = extraData.readBoolean();
                return new CookingStructureMenu(null, windowId, inventory, tier, craftable, inventoryY, canUpgrade);
            }));
}
