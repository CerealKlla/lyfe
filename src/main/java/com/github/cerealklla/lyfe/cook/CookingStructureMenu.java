package com.github.cerealklla.lyfe.cook;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.github.cerealklla.lyfe.api.Lyfe;
import com.github.cerealklla.lyfe.registration.ModAttachments;
import com.github.cerealklla.lyfe.research.PlayerResearch;
import com.github.cerealklla.lyfe.skill.Skills;
import com.github.cerealklla.lyfe.structure.FundingOption;
import com.github.cerealklla.lyfe.structure.StructureUpgradeFunding;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;

/**
 * A cooking structure's menu (design doc Section 19.3/19.6) -- direct structural mirror of {@code
 * craft.CraftingStructureMenu}'s villager-trade-style UI (known recipes listed, select one, "Cook"
 * consumes directly from the player's carried inventory), with the real differences cooking has:
 *
 * <ul>
 *   <li>A fuel requirement -- any item {@code level.fuelValues().isFuel(...)} accepts, found and
 *   consumed (1 whole item, not a real burn-duration tick count -- cooking is instant, see class
 *   doc on {@code VanillaFoodRecipeStripper}) from the player's main inventory alongside the
 *   recipe's own components, same scan/consume mechanism already used for components.</li>
 *   <li>Output count scales with structure tier (1 at T1, up to 5 at T5) -- crafting structures have
 *   no equivalent rule.</li>
 *   <li>No Quality/component-save mechanic at all -- food has no durability (Section 19.2.1's "no
 *   durability, no Quality score" rule already covers this), and Cook has no component-save perk in
 *   the design the way Crafter does.</li>
 *   <li>Icon baking ({@code CookingListener}) replaces Quality application as the "what makes this
 *   craft's output special" step.</li>
 * </ul>
 */
public class CookingStructureMenu extends AbstractContainerMenu {

    // See craft.CraftingStructureMenu's own doc for the three-funding-option design (2026-10-05).
    public static final int UPGRADE_BUTTON_ID = 0;
    public static final int UPGRADE_MIX_BUTTON_ID = 1;
    public static final int UPGRADE_GOLD_BUTTON_ID = 2;
    private static final int COOK_RECIPE_BUTTON_BASE = 100;

    // Real bug found live 2026-10-03: this previously started at 9, skipping the hotbar (slots 0-8)
    // entirely -- components/fuel sitting in the hotbar were invisible to both the availability check
    // and the actual consumption, producing "you don't have the components" even when you plainly did.
    // Fuel has no dedicated slot by design (any fuel item anywhere in the inventory works), so this
    // exclusion hit fuel doubly hard.
    private static final int MAIN_INVENTORY_START = 0;
    private static final int MAIN_INVENTORY_END = 36;

    private final CookingStructureBlockEntity structure;
    private final int tier;
    private final List<GeneratedFoodRecipe> craftable;
    private final int inventoryY;
    private final Random random = new Random();

    public CookingStructureMenu(MenuType<?> type, int containerId, Inventory inventory, CookingStructureBlockEntity structure, Player opener) {
        super(type, containerId);
        this.structure = structure;
        this.tier = structure.tier();
        this.craftable = computeCraftable(opener, tier);
        this.inventoryY = BASE_INVENTORY_Y;
        layoutSlots(inventory);
    }

    public CookingStructureMenu(MenuType<?> type, int containerId, Inventory inventory, int tier, List<GeneratedFoodRecipe> craftable, int inventoryY) {
        super(type, containerId);
        this.structure = null;
        this.tier = tier;
        this.craftable = craftable;
        this.inventoryY = inventoryY;
        layoutSlots(inventory);
    }

    public int inventoryY() {
        return inventoryY;
    }

    /**
     * Gated by the station's own tier and whether the recipe is known -- except Track A (real
     * vanilla staples), which are always treated as known (2026-10-06, "re-introduce the vanilla
     * recipes as permanent") -- the same "basic staple, everyone already knows this" treatment
     * {@code craft.EquipmentTierLadder}'s Tier 0 (Wood)/Leather already get, food just has no
     * literal Tier 0 to hang that off of.
     */
    private static List<GeneratedFoodRecipe> computeCraftable(Player player, int tier) {
        PlayerResearch research = player.getData(ModAttachments.PLAYER_RESEARCH);
        return FoodTierLadder.allResultIds().stream()
                .map(ServerFoodRecipeStore::get)
                .flatMap(java.util.Optional::stream)
                .filter(r -> r.tier() <= tier && (!FoodTierLadder.isTrackB(r.resultId()) || research.isLearned(r.resultId())))
                .toList();
    }

    public List<GeneratedFoodRecipe> craftable() {
        return craftable;
    }

    public int tier() {
        return tier;
    }

    public static final int BASE_INVENTORY_Y = 210;
    public static final int PREFERRED_IMAGE_HEIGHT = 300;
    public static final int INVENTORY_BLOCK_HEIGHT = 76;
    private static final int SCREEN_EDGE_MARGIN = 20;
    private static final int BOTTOM_MARGIN = 14;
    private static final int MIN_CONTENT_HEIGHT = 52;

    public static int computeImageHeight(int windowHeight) {
        int minImageHeight = MIN_CONTENT_HEIGHT + INVENTORY_BLOCK_HEIGHT + BOTTOM_MARGIN;
        return Math.max(minImageHeight, Math.min(PREFERRED_IMAGE_HEIGHT, windowHeight - SCREEN_EDGE_MARGIN));
    }

    public static int computeInventoryY(int windowHeight) {
        return computeImageHeight(windowHeight) - INVENTORY_BLOCK_HEIGHT - BOTTOM_MARGIN;
    }

    public static int imageHeightForInventoryY(int inventoryY) {
        return inventoryY + INVENTORY_BLOCK_HEIGHT + BOTTOM_MARGIN;
    }

    private void layoutSlots(Inventory inventory) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, inventoryY + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, inventoryY + 58));
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (structure == null || !(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        if (id == UPGRADE_BUTTON_ID) {
            return upgradeStructure(serverPlayer, FundingOption.ON_HAND);
        }
        if (id == UPGRADE_MIX_BUTTON_ID) {
            return upgradeStructure(serverPlayer, FundingOption.MIX);
        }
        if (id == UPGRADE_GOLD_BUTTON_ID) {
            return upgradeStructure(serverPlayer, FundingOption.GOLD_ONLY);
        }
        if (id >= COOK_RECIPE_BUTTON_BASE) {
            return attemptCook(serverPlayer, id - COOK_RECIPE_BUTTON_BASE);
        }
        return false;
    }

    public static int cookButtonId(int recipeIndex) {
        return COOK_RECIPE_BUTTON_BASE + recipeIndex;
    }

    private boolean attemptCook(ServerPlayer player, int recipeIndex) {
        if (recipeIndex < 0 || recipeIndex >= craftable.size()) {
            return false;
        }
        GeneratedFoodRecipe recipe = craftable.get(recipeIndex);
        var inventory = player.getInventory();

        Map<Identifier, Integer> available = new HashMap<>();
        ItemStack fuelStack = ItemStack.EMPTY;
        var fuelValues = player.level().fuelValues();
        for (int i = MAIN_INVENTORY_START; i < MAIN_INVENTORY_END; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            available.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()), stack.getCount(), Integer::sum);
            // 2026-10-06 fix (real report: "cooking just used my spear as fuel") -- vanilla's own
            // fuel registry treats every wooden tool/weapon as valid furnace fuel (real vanilla
            // behavior, not a bug there), but this auto-scan has no slot of its own to opt into --
            // it silently grabs the first fuel-eligible item anywhere in the player's main inventory,
            // which let it reach straight past "obviously fuel" items like logs/coal and burn an
            // actual held weapon instead. Damageable items (anything with durability -- every tool/
            // weapon/armor piece) are never eligible here, regardless of what the vanilla fuel
            // registry says, so only plain consumable fuel (coal, logs, planks, sticks, etc.) can
            // ever be auto-selected.
            if (fuelStack.isEmpty() && !stack.isDamageableItem() && fuelValues.isFuel(stack)) {
                fuelStack = stack;
            }
        }

        for (var entry : recipe.components().entrySet()) {
            if (available.getOrDefault(entry.getKey(), 0) < entry.getValue()) {
                player.sendSystemMessage(Component.literal("You don't have the components for this."));
                return false;
            }
        }
        if (fuelStack.isEmpty()) {
            player.sendSystemMessage(Component.literal("You need fuel to cook this."));
            return false;
        }

        for (var entry : recipe.components().entrySet()) {
            consumeFromInventory(player, entry.getKey(), entry.getValue());
        }
        fuelStack.shrink(1);

        int cookLevel = Lyfe.getLevel(player, Skills.COOK_ID);
        double icons = CookingListener.computeIcons(tier, cookLevel, recipe.slotsUsed());

        ItemStack result = new ItemStack(BuiltInRegistries.ITEM.getValue(recipe.resultId()), tier);
        CookingListener.bakeIcons(result, icons, tier);
        if (!player.getInventory().add(result)) {
            player.drop(result, false);
        }

        Lyfe.addXp(player, Skills.COOK_ID, CookingListener.xpForCraft(icons));
        return true;
    }

    private void consumeFromInventory(ServerPlayer player, Identifier itemId, int amount) {
        int remaining = amount;
        var inventory = player.getInventory();
        for (int i = MAIN_INVENTORY_START; i < MAIN_INVENTORY_END && remaining > 0; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty() || !BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(itemId)) {
                continue;
            }
            int take = Math.min(remaining, stack.getCount());
            stack.shrink(take);
            remaining -= take;
        }
    }

    private boolean upgradeStructure(ServerPlayer player, FundingOption option) {
        if (tier >= CookingStructureBlockEntity.MAX_STRUCTURE_TIER) {
            player.sendSystemMessage(Component.literal("This is already the highest tier."));
            return false;
        }
        if (!(structure.getLevel() instanceof ServerLevel level)) {
            return false;
        }
        var pos = structure.getBlockPos();
        int nextTier = tier + 1;

        StructureUpgradeFunding.Result funding = StructureUpgradeFunding.fund(player, level, pos, nextTier, option);
        if (!funding.success()) {
            player.sendSystemMessage(Component.literal(funding.message()));
            return false;
        }

        Direction facing = level.getBlockState(pos).getValue(HorizontalDirectionalBlock.FACING);
        var newState = CookingStructureBlockEntity.blockForTier(nextTier).defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing);
        level.setBlockAndUpdate(pos, newState);

        player.sendSystemMessage(Component.literal("Station upgraded to Tier " + nextTier + "!"));
        player.closeContainer();
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        ItemStack clicked = ItemStack.EMPTY;
        Slot slot = slots.get(slotIndex);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            clicked = stack.copy();
            int mainInvSize = 27;
            if (slotIndex < mainInvSize) {
                if (!moveItemStackTo(stack, mainInvSize, slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(stack, 0, mainInvSize, false)) {
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return clicked;
    }

    @Override
    public boolean stillValid(Player player) {
        if (structure == null) {
            return true;
        }
        var level = structure.getLevel();
        var pos = structure.getBlockPos();
        if (level == null || level.getBlockEntity(pos) != structure) {
            return false;
        }
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }
}
