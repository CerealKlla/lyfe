package com.github.cerealklla.lyfe.craft;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.resources.Identifier;

/**
 * Fixed, hand-authored (never randomly generated) Crafting Structure recipes for plain,
 * non-equipment conversions -- the first of these is Leaf Litter -> Charcoal (explicit user
 * request, 2026-10-18, Tier 0 -- always known, no Research Bench unlock, matching the "known,
 * vanilla recipe" intent behind the original ask; also fixes a real latent impossibility, since
 * Charcoal has no durability for {@code PassiveDurabilityResearchListener}'s damage-to-learn
 * mechanic to ever work against in the first place). A real vanilla (or vanilla-{@code
 * CraftingRecipe}-shaped) recipe
 * was tried and ruled out first: decompiling {@code ResultSlot#onTake} confirmed vanilla's own
 * crafting grid hard-codes removing exactly 1 item per occupied slot on every craft, completely
 * independent of a recipe's {@code getRemainingItems} override (that method only ever supplies an
 * optional *replacement* item, like a bucket turning into an empty bucket, never a quantity
 * override) -- so "consume a full 64-stack in one craft" is architecturally impossible through the
 * vanilla crafting-table interaction, not just the already-known 9-slot cap (see Yconomics' own
 * decisions.md for that dead end, reverted). The Crafting Structure's own menu
 * ({@code CraftingStructureMenu#attemptCraft}) directly manipulates real inventory counts in code
 * instead of going through {@code ResultSlot}, so it has no such limit.
 *
 * <p>Two more added 2026-10-21 (explicit user spec, a direct follow-up to reviewing the Tier 0
 * component pool): Cobblestone -> Flint/Gravel (a randomized-range primary output plus a fixed
 * secondary one, see {@link GeneratedRecipe}'s own class doc for that mechanism) and
 * Gravel -> Cobblestone (the original fixed-single-output shape).
 *
 * <p>Unlike every other entry {@code ServerRecipeStore#generate} produces, these are NOT tied to
 * {@code EquipmentTierLadder}/{@code SocketToolRegistry} at all -- {@code
 * CraftingStructureMenu#attemptCraft}'s Quality/durability-override/armor-rebalance
 * post-processing already self-guards against a result that's neither damageable nor armor
 * ({@code QualityApplier#apply}'s own {@code isDamageableItem} check, and {@code
 * EquipmentTierLadder#armorTypeOf}/{@code #baseDurabilityOverride} both resolving to "no-op" for an
 * unrecognized id/tier), so no extra gating was needed there -- these ids just need to be listed
 * alongside the equipment ones in {@code CraftingStructureMenu#computeCraftable} so the recipe
 * actually shows up once researched.
 */
public final class MiscRecipes {

    public static final Identifier LEAF_LITTER_TO_CHARCOAL = Identifier.withDefaultNamespace("charcoal");

    // Two new Tier 0 conversions, 2026-10-21 (explicit user spec, after playtesting the Tier 0
    // component pool) -- same "always known" Tier 0 shape as Leaf Litter -> Charcoal above.
    public static final Identifier COBBLESTONE_TO_FLINT_AND_GRAVEL = Identifier.withDefaultNamespace("flint");
    public static final Identifier GRAVEL_TO_COBBLESTONE = Identifier.withDefaultNamespace("cobblestone");

    private MiscRecipes() {
    }

    public static List<Identifier> allIds() {
        return List.of(LEAF_LITTER_TO_CHARCOAL, COBBLESTONE_TO_FLINT_AND_GRAVEL, GRAVEL_TO_COBBLESTONE);
    }

    static Map<Identifier, GeneratedRecipe> generate() {
        return Map.of(
                LEAF_LITTER_TO_CHARCOAL, new GeneratedRecipe(
                        LEAF_LITTER_TO_CHARCOAL,
                        0,
                        Map.of(Identifier.withDefaultNamespace("leaf_litter"), 64),
                        Map.of(),
                        10),
                // "5 cobblestone becomes 1-3 flint and 4 gravel" -- Flint is the primary (randomized)
                // output, Gravel the secondary (fixed) one; see GeneratedRecipe's own class doc for
                // why a single optional secondary output (not a general list) is enough here.
                COBBLESTONE_TO_FLINT_AND_GRAVEL, new GeneratedRecipe(
                        COBBLESTONE_TO_FLINT_AND_GRAVEL,
                        0,
                        Map.of(Identifier.withDefaultNamespace("cobblestone"), 5),
                        Map.of(),
                        1, 3,
                        Optional.of(Identifier.withDefaultNamespace("gravel")), 4),
                // "9 gravel becomes 1 cobblestone" -- plain fixed single output, the original shape.
                GRAVEL_TO_COBBLESTONE, new GeneratedRecipe(
                        GRAVEL_TO_COBBLESTONE,
                        0,
                        Map.of(Identifier.withDefaultNamespace("gravel"), 9),
                        Map.of(),
                        1));
    }
}
