package com.github.cerealklla.lyfe.research;

import java.util.concurrent.ThreadLocalRandom;

import com.github.cerealklla.lyfe.api.Lyfe;
import com.github.cerealklla.lyfe.craft.GeneratedRecipe;
import com.github.cerealklla.lyfe.craft.ServerRecipeStore;
import com.github.cerealklla.lyfe.registration.ModAttachments;
import com.github.cerealklla.lyfe.registration.ModItems;
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
 * A loot-found note tied to one specific recipe (2026-10-03, user request): right-clicking rolls a
 * research attempt (1 base Research Point, same crit-fail/crit-success curve a real Research Bench
 * attempt uses -- see {@link ResearchCalculator#roll}) toward that recipe and consumes the note, if
 * not already known; otherwise it's a no-op and the note is NOT consumed (a wasted click, not a
 * wasted item).
 *
 * <p>Deliberately bypasses {@code craft.CrafterConstants#researchUnlockedTier} entirely -- confirmed
 * with the user: Research Notes are the intended early-unlock path for a recipe above the player's
 * current Crafter level, metered by loot rarity ({@code loot.ResearchNoteLootInjector}) instead of
 * by level. No code in this class ever checks that gate. The Researcher skill's % bonuses DO still
 * apply here though (2026-10-03, explicit user correction) -- same crit curve as the Bench, just no
 * durability-save roll (there's no item stack to damage).
 */
public class ResearchNoteItem extends Item {

    public ResearchNoteItem(Properties properties) {
        super(properties);
    }

    /** The only way a Research Note stack is ever constructed -- see {@code loot.ResearchNoteLootInjector}. */
    public static ItemStack createFor(Identifier resultId, int tier) {
        ItemStack stack = new ItemStack(ModItems.RESEARCH_NOTES.get());
        stack.set(ModItems.RESEARCH_NOTE_TARGET.get(), new ResearchNoteTarget(resultId, tier));
        return stack;
    }

    @Override
    public Component getName(ItemStack stack) {
        ResearchNoteTarget target = stack.get(ModItems.RESEARCH_NOTE_TARGET.get());
        if (target == null) {
            return Component.literal("Research Notes");
        }
        Item resultItem = BuiltInRegistries.ITEM.getValue(target.resultId());
        String name = new ItemStack(resultItem).getHoverName().getString();
        return Component.literal("Research Notes - " + name + " (Tier " + target.tier() + ")");
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
        ResearchNoteTarget target = stack.get(ModItems.RESEARCH_NOTE_TARGET.get());
        if (target == null) {
            return InteractionResult.FAIL;
        }
        if (target.tier() == 0) {
            // Wood (tools) / Leather (armor) are auto-known -- defensive guard, consistent with
            // ResearchMenu's own, even though today's loot injector never targets Tier 0.
            serverPlayer.sendSystemMessage(Component.literal("You already know how to craft this."));
            return InteractionResult.SUCCESS_SERVER;
        }

        var research = serverPlayer.getData(ModAttachments.PLAYER_RESEARCH);
        if (research.isLearned(target.resultId())) {
            serverPlayer.sendSystemMessage(Component.literal("You already know how to craft this."));
            return InteractionResult.SUCCESS_SERVER;
        }

        GeneratedRecipe recipe = ServerRecipeStore.get(target.resultId()).orElse(null);
        int tier = recipe != null ? recipe.tier() : target.tier();
        int threshold = ResearcherConstants.thresholdForTier(tier);

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
            serverPlayer.sendSystemMessage(Component.literal("You've learned how to craft this recipe!"));
        } else if (outcome.criticalSuccess()) {
            serverPlayer.sendSystemMessage(Component.literal("Critical success! Real progress made."));
        } else {
            serverPlayer.sendSystemMessage(Component.literal("You study the notes and learn a bit more about crafting this."));
        }
        return InteractionResult.SUCCESS_SERVER;
    }
}
