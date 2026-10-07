package com.github.cerealklla.lyfe.cook;

import java.util.concurrent.ThreadLocalRandom;

import com.github.cerealklla.lyfe.api.Lyfe;
import com.github.cerealklla.lyfe.registration.ModAttachments;
import com.github.cerealklla.lyfe.registration.ModItems;
import com.github.cerealklla.lyfe.research.PlayerResearch;
import com.github.cerealklla.lyfe.research.ResearchCalculator;
import com.github.cerealklla.lyfe.research.ResearchOutcome;
import com.github.cerealklla.lyfe.research.ResearchProgressPayload;
import com.github.cerealklla.lyfe.research.ResearcherConstants;
import com.github.cerealklla.lyfe.skill.Skills;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * A loot-found note tied to one specific cooking recipe -- direct mirror of {@code
 * research.ResearchNoteItem}, kept as its own distinct item/component so Recipe Notes have a
 * separate dynamic name/icon from equipment Research Notes. Right-clicking rolls a research
 * attempt toward that recipe and consumes the note, if not already known; otherwise a no-op, note
 * not consumed. **Deliberately bypasses {@code CookConstants#researchUnlockedTier} entirely** --
 * same bypass equipment Research Notes get, confirmed consistent with the design's "one deliberate
 * way to bypass this gate" framing. Uses the same {@code /10}-of-equipment-threshold reduction
 * {@code research.ResearchMenu} already applies to every non-durability item, matching "a lot
 * easier to learn a Cooking Recipe" (2026-10-03 user request).
 */
public class RecipeNoteItem extends Item {

    public RecipeNoteItem(Properties properties) {
        super(properties);
    }

    public static ItemStack createFor(Identifier resultId, int tier) {
        ItemStack stack = new ItemStack(ModItems.RECIPE_NOTES.get());
        stack.set(ModItems.RECIPE_NOTE_TARGET.get(), new RecipeNoteTarget(resultId, tier));
        return stack;
    }

    @Override
    public Component getName(ItemStack stack) {
        RecipeNoteTarget target = stack.get(ModItems.RECIPE_NOTE_TARGET.get());
        if (target == null) {
            return Component.literal("Recipe Notes");
        }
        Item resultItem = BuiltInRegistries.ITEM.getValue(target.resultId());
        String name = new ItemStack(resultItem).getHoverName().getString();
        return Component.literal("Recipe Notes - " + name + " (Tier " + target.tier() + ")");
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }

        ItemStack stack = player.getItemInHand(hand);
        RecipeNoteTarget target = stack.get(ModItems.RECIPE_NOTE_TARGET.get());
        if (target == null) {
            return InteractionResult.FAIL;
        }

        var research = serverPlayer.getData(ModAttachments.PLAYER_RESEARCH);
        if (research.isLearned(target.resultId())) {
            serverPlayer.sendSystemMessage(Component.literal("You already know how to cook this."));
            return InteractionResult.SUCCESS_SERVER;
        }

        GeneratedFoodRecipe recipe = ServerFoodRecipeStore.get(target.resultId()).orElse(null);
        int tier = recipe != null ? recipe.tier() : target.tier();
        int threshold = Math.max(1, ResearcherConstants.thresholdForTier(tier) / 10);

        int researcherLevel = Lyfe.getLevel(serverPlayer, Skills.RESEARCHER_ID);
        ResearchOutcome outcome = ResearchCalculator.roll(researcherLevel, Skills.MAX_LEVEL, 1, ThreadLocalRandom.current());

        int oldPoints = research.researchPoints(target.resultId());
        stack.shrink(1);
        research.addResearchPoints(target.resultId(), outcome.pointsGranted(), threshold);
        int newPoints = research.isLearned(target.resultId()) ? threshold : research.researchPoints(target.resultId());
        PacketDistributor.sendToPlayer(serverPlayer, new ResearchProgressPayload(target.resultId(), oldPoints, newPoints, threshold));

        if (outcome.criticalFailure()) {
            serverPlayer.sendSystemMessage(Component.literal("Critical failure -- you learned nothing from these notes."));
        } else if (research.isLearned(target.resultId())) {
            serverPlayer.sendSystemMessage(Component.literal("You've learned how to cook this recipe!"));
        } else if (outcome.criticalSuccess()) {
            serverPlayer.sendSystemMessage(Component.literal("Critical success! Real progress made."));
        } else {
            serverPlayer.sendSystemMessage(Component.literal("You study the notes and learn a bit more about cooking this."));
        }
        return InteractionResult.SUCCESS_SERVER;
    }
}
