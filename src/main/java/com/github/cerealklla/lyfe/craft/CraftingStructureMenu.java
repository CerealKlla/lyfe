package com.github.cerealklla.lyfe.craft;

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
 * A crafting structure's menu (design doc Section 19.6) -- **redesigned 2026-10-02 per explicit user
 * request** to mirror a villager-trade/recipe-book style UI instead of a manual ingredient-grid: the
 * player's known, tier-eligible recipes are listed (computed once here, server-side, and sent to the
 * client via {@code CraftingStructureBlockEntity#writeClientSideData}); selecting one shows its cost
 * on {@code CraftingStructureScreen}; "Craft" consumes directly from the player's own carried
 * inventory -- no manual ingredient placement, no output slot to manually collect from.
 */
public class CraftingStructureMenu extends AbstractContainerMenu {

    // Three funding options (2026-10-05, explicit user spec) -- see structure.FundingOption/
    // StructureUpgradeFunding for the actual cost rules. UPGRADE_BUTTON_ID kept as the original
    // name/id (now meaning "on-hand only") to minimize churn at existing call sites.
    public static final int UPGRADE_BUTTON_ID = 0;
    public static final int UPGRADE_MIX_BUTTON_ID = 1;
    public static final int UPGRADE_GOLD_BUTTON_ID = 2;
    private static final int CRAFT_RECIPE_BUTTON_BASE = 100;
    private static final int CRAFTER_XP_PER_CRAFT = 20;

    // Main-inventory-only crafting (2026-10-03, user request): confirmed via the real decompiled
    // Inventory.java that player.getInventory().getContainerSize() spans the hotbar (0-8) + main
    // inventory (9-35) AND equipped armor/offhand (36+, via EQUIPMENT_SLOT_MAPPING) all in one
    // transparent range -- scanning/consuming the full range (the old behavior) could silently eat a
    // sword sitting in the hotbar or the armor a player is wearing, and critically could also eat
    // the new upgrade-chain base-item requirement (see RecipeGenerator) right out of active use.
    // Scoped to just the 27 main-inventory slots.
    private static final int MAIN_INVENTORY_START = 9;
    private static final int MAIN_INVENTORY_END = 36;

    private final CraftingStructureBlockEntity structure;
    private final int tier;
    private final List<GeneratedRecipe> craftable;
    private final int inventoryY;
    private final boolean canUpgrade;
    private final Random random = new Random();

    /** Server-side: computes the known+eligible recipe list once, up front. */
    public CraftingStructureMenu(MenuType<?> type, int containerId, Inventory inventory, CraftingStructureBlockEntity structure, Player opener) {
        super(type, containerId);
        this.structure = structure;
        this.tier = structure.tier();
        this.craftable = computeCraftable(opener, tier);
        // Cosmetic only (Slot x/y never affects server-authoritative click handling) -- the real
        // per-client value is computed client-side, see the other constructor.
        this.inventoryY = BASE_INVENTORY_Y;
        this.canUpgrade = opener instanceof ServerPlayer serverOpener && structure.getLevel() instanceof ServerLevel serverLevel
                && StructureUpgradeFunding.canUpgrade(serverOpener, serverLevel, structure.getBlockPos(), tier);
        layoutSlots(inventory);
    }

    /**
     * Client-side reconstruction (see {@code registration.ModMenus}) -- {@code tier}/{@code craftable}/
     * {@code canUpgrade} (added 2026-10-09, gates the Upgrade button(s) -- see {@code
     * structure.StructureUpgradeFunding#canUpgrade}'s own doc) come from {@code
     * writeClientSideData}. {@code inventoryY} is computed by the client factory from the player's
     * real window size (2026-10-03 fix, see {@link #computeInventoryY}'s doc) -- {@code Slot.x}/
     * {@code Slot.y} are final in this version, so the layout must be correct at construction time;
     * it can't be shifted afterward the way a mutable field could.
     */
    public CraftingStructureMenu(MenuType<?> type, int containerId, Inventory inventory, int tier, List<GeneratedRecipe> craftable, int inventoryY, boolean canUpgrade) {
        super(type, containerId);
        this.structure = null;
        this.tier = tier;
        this.craftable = craftable;
        this.inventoryY = inventoryY;
        this.canUpgrade = canUpgrade;
        layoutSlots(inventory);
    }

    public boolean canUpgrade() {
        return canUpgrade;
    }

    public int inventoryY() {
        return inventoryY;
    }

    /**
     * Gated only by the station's own tier and whether the recipe is known -- Crafter level no
     * longer limits what can be *crafted* at all (removed 2026-10-03, explicit user correction: the
     * level-based tier gate was only ever meant to apply to *researching* a real item at the Research
     * Bench, not to crafting -- "If you have the recipe, you can craft it at the appropriate crafting
     * station." See {@code research.ResearchMenu}/{@code CrafterConstants#researchUnlockedTier} for
     * the gate that actually does apply, and {@code research.ResearchNoteItem} for the one way to
     * bypass it. Tier 0 (Wood for tools, Leather for armor) is always includable regardless of
     * {@code PlayerResearch} state -- auto-known, per the user's explicit decision (confirmed again
     * 2026-10-05 after a brief reversal -- see decisions.md).
     */
    private static List<GeneratedRecipe> computeCraftable(Player player, int tier) {
        PlayerResearch research = player.getData(ModAttachments.PLAYER_RESEARCH);

        return java.util.stream.Stream.of(
                        EquipmentTierLadder.allGeneratedItemIds().stream(),
                        EquipmentTierLadder.allGeneratedArmorItemIds().stream(),
                        EquipmentTierLadder.allGeneratedRangedWeaponIds().stream())
                .flatMap(s -> s)
                .map(ServerRecipeStore::get)
                .flatMap(java.util.Optional::stream)
                .filter(r -> r.tier() <= tier && (r.tier() == 0 || research.isLearned(r.resultId())))
                .toList();
    }

    public List<GeneratedRecipe> craftable() {
        return craftable;
    }

    public int tier() {
        return tier;
    }

    // Default/server-side inventory-row Y -- see the client constructor's doc. Kept as the fallback
    // baseline that CraftingStructureScreen's own PREFERRED_IMAGE_HEIGHT (300) was designed against.
    public static final int BASE_INVENTORY_Y = 210;

    // Pure int math, no client API calls -- shared by ModMenus' client-only menu factory (which
    // passes the result into the constructor above) and CraftingStructureScreen's own layout, so
    // there's exactly one source of truth for the "shrink to fit" formula (2026-10-03 fix: a fixed
    // 300-tall panel overflowed some players' screens at Auto-GUI-Scale resolutions where the chosen
    // scale left a logical window height between vanilla's guaranteed 240 floor and this panel's 300
    // design height). PREFERRED_IMAGE_HEIGHT/INVENTORY_BLOCK_HEIGHT/the two margins mirror
    // CraftingStructureScreen's own layout constants exactly -- keep them in sync if either changes.
    public static final int PREFERRED_IMAGE_HEIGHT = 300;
    public static final int INVENTORY_BLOCK_HEIGHT = 76;
    private static final int SCREEN_EDGE_MARGIN = 20;
    private static final int BOTTOM_MARGIN = 14;
    // Mirrors the screen's GRID_Y (26) + one ICON_CELL (26) -- the smallest content area that still
    // shows at least one row of recipes above the inventory block.
    private static final int MIN_CONTENT_HEIGHT = 52;

    public static int computeImageHeight(int windowHeight) {
        int minImageHeight = MIN_CONTENT_HEIGHT + INVENTORY_BLOCK_HEIGHT + BOTTOM_MARGIN;
        return Math.max(minImageHeight, Math.min(PREFERRED_IMAGE_HEIGHT, windowHeight - SCREEN_EDGE_MARGIN));
    }

    public static int computeInventoryY(int windowHeight) {
        return computeImageHeight(windowHeight) - INVENTORY_BLOCK_HEIGHT - BOTTOM_MARGIN;
    }

    /**
     * The inverse of {@link #computeInventoryY} -- used by {@code CraftingStructureScreen} to derive
     * its own {@code imageHeight} from the menu's already-fixed {@code inventoryY} (set once at
     * construction, since {@code Slot.y} is final) rather than recomputing independently from the
     * window's CURRENT size. That distinction matters if the player resizes their window while the
     * menu is still open: {@code inventoryY} can't change after construction either way, so deriving
     * imageHeight from it instead of from a possibly-since-changed window size keeps the two always
     * self-consistent (never overflowing), at the cost of the panel simply not resizing live -- an
     * accepted, documented limitation, not a bug.
     */
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
        if (id >= CRAFT_RECIPE_BUTTON_BASE) {
            return attemptCraft(serverPlayer, id - CRAFT_RECIPE_BUTTON_BASE);
        }
        return false;
    }

    public static int craftButtonId(int recipeIndex) {
        return CRAFT_RECIPE_BUTTON_BASE + recipeIndex;
    }

    private boolean attemptCraft(ServerPlayer player, int recipeIndex) {
        if (recipeIndex < 0 || recipeIndex >= craftable.size()) {
            return false;
        }
        GeneratedRecipe recipe = craftable.get(recipeIndex);

        Map<Identifier, Integer> available = new HashMap<>();
        var inventory = player.getInventory();
        for (int i = MAIN_INVENTORY_START; i < MAIN_INVENTORY_END; i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) {
                available.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()), stack.getCount(), Integer::sum);
            }
        }
        for (var entry : recipe.specificComponents().entrySet()) {
            if (available.getOrDefault(entry.getKey(), 0) < entry.getValue()) {
                player.sendSystemMessage(Component.literal("You don't have the components for this."));
                return false;
            }
        }
        for (var entry : recipe.genericComponents().entrySet()) {
            int haveTotal = ComponentGroups.membersOf(entry.getKey()).stream()
                    .mapToInt(id -> available.getOrDefault(id, 0))
                    .sum();
            if (haveTotal < entry.getValue()) {
                player.sendSystemMessage(Component.literal("You don't have the components for this."));
                return false;
            }
        }

        int crafterLevel = Lyfe.getLevel(player, Skills.CRAFTER_ID);
        double saveChance = CrafterConstants.componentSaveChance(crafterLevel, Skills.MAX_LEVEL);
        int saved = 0;
        for (var entry : recipe.specificComponents().entrySet()) {
            int toConsume = entry.getValue();
            if (saved < CrafterConstants.MAX_COMPONENTS_SAVED_PER_CRAFT && random.nextDouble() < saveChance) {
                toConsume = Math.max(0, toConsume - 1);
                saved++;
            }
            consumeFromInventory(player, entry.getKey(), toConsume);
        }
        for (var entry : recipe.genericComponents().entrySet()) {
            int toConsume = entry.getValue();
            if (saved < CrafterConstants.MAX_COMPONENTS_SAVED_PER_CRAFT && random.nextDouble() < saveChance) {
                toConsume = Math.max(0, toConsume - 1);
                saved++;
            }
            for (Identifier memberId : ComponentGroups.membersOf(entry.getKey())) {
                if (toConsume <= 0) {
                    break;
                }
                int haveOfMember = available.getOrDefault(memberId, 0);
                int take = Math.min(toConsume, haveOfMember);
                if (take > 0) {
                    consumeFromInventory(player, memberId, take);
                    toConsume -= take;
                }
            }
        }

        double quality = CrafterConstants.qualityFromLevel(crafterLevel, Skills.MAX_LEVEL)
                + CrafterConstants.structureBonus(tier);
        ItemStack result = new ItemStack(BuiltInRegistries.ITEM.getValue(recipe.resultId()));
        var armorType = EquipmentTierLadder.armorTypeOf(recipe.resultId());
        if (armorType == null) {
            // Tool/weapon only -- custom base-durability override (2026-10-06), applied before
            // Quality's own % bonus so Quality boosts off the custom base, not vanilla's raw value.
            int baseDurabilityOverride = EquipmentTierLadder.baseDurabilityOverride(recipe.tier());
            if (baseDurabilityOverride > 0) {
                result.set(net.minecraft.core.component.DataComponents.MAX_DAMAGE, baseDurabilityOverride);
            }
        }
        QualityApplier.apply(result, quality);
        if (armorType != null) {
            result.set(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS,
                    ArmorRebalance.attributesFor(armorType, recipe.tier()));
        }
        if (!player.getInventory().add(result)) {
            player.drop(result, false);
        }

        Lyfe.addXp(player, Skills.CRAFTER_ID, CRAFTER_XP_PER_CRAFT);
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

    /**
     * "Upgrade Station" (user request, 2026-10-02; given a real resource cost 2026-10-05, see
     * {@code structure.StructureUpgradeFunding}) -- replaces the placed block in-world with the
     * next tier up, preserving facing, once {@code option}'s funding requirement is met. Free and
     * instant if the structure isn't on a real Settlemynts plot at all (graceful degradation, see
     * {@code StructureUpgradeFunding#fund}'s own doc) -- otherwise instant once funded, no
     * construction-style time gate (deliberate simplification, not matching Blueprynts' plot-tier
     * timing model). Closes the menu afterward since the old {@code structure} reference is stale
     * the instant the block is replaced.
     */
    private boolean upgradeStructure(ServerPlayer player, FundingOption option) {
        // Compared against the STRUCTURE ladder's own max (2026-10-03 fix), not
        // EquipmentTierLadder.MAX_TIER -- those two counts coincidentally matched before Copper's
        // insertion made the equipment ladder longer than the 5 real physical structure blocks;
        // using the wrong one here would let a Tier 5 structure try to upgrade to a nonexistent
        // Tier 6 block and crash.
        if (tier >= CraftingStructureBlockEntity.MAX_STRUCTURE_TIER) {
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
        var newState = CraftingStructureBlockEntity.blockForTier(nextTier).defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing);
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
