package com.github.cerealklla.lyfe.cook;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import com.github.cerealklla.lyfe.registration.ModAttachments;
import com.github.cerealklla.lyfe.research.PlayerResearch;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Grants every player a one-time, free "starter pack" of 3 random Tier 1 cooking recipes, learned
 * outright (no Research Bench grind) -- added 2026-10-05, explicit user request: "when the server
 * first generates the recipes (e.g. after a server wipe) that it should also give everyone knowledge
 * of 3 Tier 1 cooking recipes automatically."
 *
 * <p><b>Implemented as a per-player login check, not a one-shot hook on {@code
 * ServerFoodRecipeStore}'s generation event</b> -- a genuinely fresh generation (a true server wipe)
 * happens at server boot, before any player has logged back in to receive anything; the only way to
 * actually reach "everyone" is to grant each player their pack the moment they're next seen, gated on
 * {@link PlayerResearch#hasReceivedFoodStarterPack()} so it only ever fires once per player (a
 * genuine server wipe resets this flag too, since it resets the player's whole attachment).
 */
public final class FoodStarterPackListener {

    private static final int STARTER_PACK_SIZE = 3;
    private static final int STARTER_TIER = 1;

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        PlayerResearch research = player.getData(ModAttachments.PLAYER_RESEARCH);
        if (research.hasReceivedFoodStarterPack()) {
            return;
        }
        List<Identifier> tier1Recipes = new ArrayList<>(FoodTierLadder.allResultIds().stream()
                .filter(id -> FoodTierLadder.tierOf(id) == STARTER_TIER)
                .toList());
        Collections.shuffle(tier1Recipes, new Random());
        int granted = 0;
        for (Identifier id : tier1Recipes) {
            if (granted >= STARTER_PACK_SIZE) {
                break;
            }
            if (ServerFoodRecipeStore.get(id).isPresent() && research.learnDirectly(id, STARTER_TIER)) {
                granted++;
            }
        }
        research.markFoodStarterPackGranted();
    }
}
