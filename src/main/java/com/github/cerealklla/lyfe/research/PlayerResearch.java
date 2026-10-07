package com.github.cerealklla.lyfe.research;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;

/**
 * A single player's research progress (design doc Section 19.5) -- per-item Research Points
 * progress, plus the set of recipe ids actually learned. Mirrors {@code knowledge.PlayerKnowledge}'s
 * own attachment shape. Not synced -- server-only logic, same reasoning as {@code PlayerKnowledge}
 * (no client rendering of this data in this slice).
 *
 * <p>"Known recipe" is a hard per-player requirement (confirmed 2026-10-02, not a convenience/
 * discovery aid) -- {@link #isLearned} is the actual crafting-time gate, not just a UI hint.
 */
public final class PlayerResearch {

    public static final int SCHEMA_VERSION = 1;

    public static final MapCodec<PlayerResearch> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.fieldOf("schema_version").forGetter(p -> p.schemaVersion),
            Codec.unboundedMap(Identifier.CODEC, Codec.INT).fieldOf("research_points").forGetter(p -> p.researchPoints),
            Codec.list(Identifier.CODEC).fieldOf("learned_recipes").forGetter(p -> java.util.List.copyOf(p.learnedRecipes)),
            Codec.BOOL.optionalFieldOf("received_food_starter_pack", false).forGetter(p -> p.receivedFoodStarterPack)
    ).apply(i, PlayerResearch::new));

    private final int schemaVersion;
    private final Map<Identifier, Integer> researchPoints;
    private final Set<Identifier> learnedRecipes;
    private boolean receivedFoodStarterPack;

    public PlayerResearch() {
        this(SCHEMA_VERSION, new HashMap<>(), java.util.List.of(), false);
    }

    private PlayerResearch(int schemaVersion, Map<Identifier, Integer> researchPoints, java.util.List<Identifier> learnedRecipes, boolean receivedFoodStarterPack) {
        this.schemaVersion = schemaVersion;
        this.researchPoints = new HashMap<>(researchPoints);
        this.learnedRecipes = new HashSet<>(learnedRecipes);
        this.receivedFoodStarterPack = receivedFoodStarterPack;
    }

    public boolean isLearned(Identifier itemId) {
        return learnedRecipes.contains(itemId);
    }

    public int researchPoints(Identifier itemId) {
        return researchPoints.getOrDefault(itemId, 0);
    }

    /**
     * Adds Research Points toward {@code itemId}, and moves it into {@code learnedRecipes} once it
     * crosses {@code threshold}. No-op once already learned -- confirmed anti-farming rule,
     * 2026-10-02: re-researching an already-learned item grants zero further progress.
     *
     * @return true if this attempt actually did anything (was not already learned).
     */
    public boolean addResearchPoints(Identifier itemId, int amount, int threshold) {
        if (learnedRecipes.contains(itemId)) {
            return false;
        }
        int updated = researchPoints(itemId) + Math.max(0, amount);
        if (updated >= threshold) {
            researchPoints.remove(itemId);
            learnedRecipes.add(itemId);
        } else {
            researchPoints.put(itemId, updated);
        }
        return true;
    }

    /**
     * Marks {@code itemId} (a Tier {@code tier} recipe) as learned outright, skipping the threshold
     * grind entirely -- debug/testing only (see {@code debug.DebugCommands}'s {@code learnrecipes}).
     * Keeps {@code ResearcherConstants} (package-private) out of the debug package's imports.
     *
     * @return true if this actually did anything (was not already learned).
     */
    public boolean learnDirectly(Identifier itemId, int tier) {
        int threshold = ResearcherConstants.thresholdForTier(tier);
        return addResearchPoints(itemId, threshold, threshold);
    }

    /**
     * Whether this player has already received their one-time 3-recipe Tier 1 cooking starter pack
     * (added 2026-10-05, explicit user request -- "when the server first generates the recipes
     * ...give everyone knowledge of 3 Tier 1 cooking recipes automatically"). Tracked as its own flag
     * rather than inferred from {@code learnedRecipes} being empty -- a player who genuinely never
     * learns a recipe (or a non-cooking recipe only) must not be re-granted the pack on every login.
     */
    public boolean hasReceivedFoodStarterPack() {
        return receivedFoodStarterPack;
    }

    public void markFoodStarterPackGranted() {
        receivedFoodStarterPack = true;
    }
}
