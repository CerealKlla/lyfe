package com.github.cerealklla.lyfe.research;

import java.util.Random;

import com.github.cerealklla.lyfe.api.Lyfe;
import com.github.cerealklla.lyfe.cook.CookConstants;
import com.github.cerealklla.lyfe.cook.ServerFoodRecipeStore;
import com.github.cerealklla.lyfe.craft.CrafterConstants;
import com.github.cerealklla.lyfe.craft.ServerRecipeStore;
import com.github.cerealklla.lyfe.registration.ModAttachments;
import com.github.cerealklla.lyfe.skill.Skills;
import com.github.cerealklla.lyfe.skill.SkillId;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Research Bench's menu (design doc Section 19.5): one input slot, a "Research" button
 * ({@link #clickMenuButton}, id 0), and player inventory. Research logic lives here rather than a
 * separate listener since it's entirely interaction-driven, not tick-driven -- no other skill's
 * "hand-build your own listener" precedent fits a menu-button action better than the menu itself.
 */
public class ResearchMenu extends AbstractContainerMenu {

    // Fixed for proper alignment with the "Research" button, 2026-10-02 -- see ResearchScreen's own
    // button bounds, which are centered against this exact slot position.
    private static final int INPUT_SLOT_X = 16;
    private static final int INPUT_SLOT_Y = 34;
    private static final int PLAYER_INV_Y = 66;
    public static final int RESEARCH_BUTTON_ID = 0;

    private final Container inputContainer;
    private final ResearchBenchBlockEntity bench;
    private final Random random = new Random();

    public ResearchMenu(MenuType<?> type, int containerId, Inventory inventory, Container inputContainer, ResearchBenchBlockEntity bench) {
        super(type, containerId);
        this.inputContainer = inputContainer;
        this.bench = bench;
        layoutSlots(inventory);
    }

    /** Client-side reconstruction (see {@code registration.ModMenus}) -- no real bench to read from. */
    public ResearchMenu(MenuType<?> type, int containerId, Inventory inventory) {
        this(type, containerId, inventory, new SimpleContainer(1), null);
    }

    private void layoutSlots(Inventory inventory) {
        addSlot(new Slot(inputContainer, 0, INPUT_SLOT_X, INPUT_SLOT_Y));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, PLAYER_INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, PLAYER_INV_Y + 58));
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id != RESEARCH_BUTTON_ID || !(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        return attemptResearch(serverPlayer);
    }

    private boolean attemptResearch(ServerPlayer player) {
        ItemStack stack = inputContainer.getItem(0);
        if (stack.isEmpty()) {
            return false;
        }
        Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());

        // Extended 2026-10-03 (cooking overhaul) to also look up the food recipe store -- whichever
        // store matches supplies the tier and which skill/table gates researching it; the existing
        // "no durability -> destroy immediately, /10 threshold" branch below already selects food's
        // real behavior with no further branching needed, since it keys off the item itself
        // (isDamageableItem), not which store matched.
        Integer tier = null;
        SkillId gateSkillId = null;
        java.util.function.IntUnaryOperator unlockedTierFn = null;

        var equipRecipe = ServerRecipeStore.get(itemId);
        if (equipRecipe.isPresent()) {
            tier = equipRecipe.get().tier();
            gateSkillId = Skills.CRAFTER_ID;
            unlockedTierFn = CrafterConstants::researchUnlockedTier;
        } else {
            var foodRecipe = ServerFoodRecipeStore.get(itemId);
            if (foodRecipe.isPresent()) {
                tier = foodRecipe.get().tier();
                gateSkillId = Skills.COOK_ID;
                unlockedTierFn = CookConstants::researchUnlockedTier;
            }
        }
        if (tier == null) {
            player.sendSystemMessage(Component.literal("There is nothing to learn from this."));
            return false;
        }

        if (tier == 0) {
            // Wood (tools) / Leather (armor) are auto-known (2026-10-03/2026-10-04, user decision,
            // reconfirmed 2026-10-05) -- never enters learnedRecipes, so without this guard a
            // repeated attempt would just grind away durability for zero possible payoff. Same
            // message the real already-learned branch below uses.
            player.sendSystemMessage(Component.literal("You already know how to craft this."));
            return false;
        }

        var research = player.getData(ModAttachments.PLAYER_RESEARCH);
        if (research.isLearned(itemId)) {
            player.sendSystemMessage(Component.literal("You already know how to craft/cook this."));
            return false;
        }

        int gateLevel = Lyfe.getLevel(player, gateSkillId);
        if (tier > unlockedTierFn.applyAsInt(gateLevel)) {
            player.sendSystemMessage(Component.literal("This recipe is too advanced for your current skill."));
            return false;
        }

        int level = Lyfe.getLevel(player, Skills.RESEARCHER_ID);
        ResearchOutcome outcome = ResearchCalculator.roll(level, Skills.MAX_LEVEL, ResearcherConstants.FLAT_RESEARCH_POINTS_PER_ATTEMPT, random);

        boolean foodOrNoDurability = !stack.isDamageableItem();
        int threshold = foodOrNoDurability
                ? Math.max(1, ResearcherConstants.thresholdForTier(tier) / 10)
                : ResearcherConstants.thresholdForTier(tier);

        if (foodOrNoDurability) {
            inputContainer.setItem(0, ItemStack.EMPTY);
        } else if (!outcome.durabilitySaved()) {
            int damage = ResearchCalculator.durabilityDamage(stack.getMaxDamage(), stack.getDamageValue(), tier, outcome.criticalFailure(), random);
            stack.setDamageValue(Math.min(stack.getMaxDamage() - 1, stack.getDamageValue() + damage));
            if (stack.getDamageValue() >= stack.getMaxDamage() - 1) {
                inputContainer.setItem(0, ItemStack.EMPTY);
            }
        }

        int oldPoints = research.researchPoints(itemId);
        Lyfe.addXp(player, Skills.RESEARCHER_ID, ResearcherConstants.RESEARCH_XP_PER_ATTEMPT);
        research.addResearchPoints(itemId, outcome.pointsGranted(), threshold);
        int newPoints = research.isLearned(itemId) ? threshold : research.researchPoints(itemId);
        PacketDistributor.sendToPlayer(player, new ResearchProgressPayload(itemId, oldPoints, newPoints, threshold));

        if (outcome.criticalFailure()) {
            player.sendSystemMessage(Component.literal("Critical failure -- you learned nothing from this attempt."));
        } else if (research.isLearned(itemId)) {
            player.sendSystemMessage(Component.literal("You've learned how to craft this recipe!"));
        } else if (outcome.criticalSuccess()) {
            player.sendSystemMessage(Component.literal("Critical success! Real progress made."));
        }
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        ItemStack clicked = ItemStack.EMPTY;
        Slot slot = slots.get(slotIndex);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            clicked = stack.copy();
            if (slotIndex == 0) {
                if (!moveItemStackTo(stack, 1, slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(stack, 0, 1, false)) {
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
        return bench == null || inputContainer.stillValid(player);
    }
}
