package com.github.cerealklla.lyfe.cook;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.github.cerealklla.lyfe.LyfeMod;

import net.minecraft.resources.Identifier;

/**
 * The cooking tier ladder (design doc Section 19.3/19.6), two parallel result tracks per tier 1-5:
 *
 * <ul>
 *   <li><b>Track A (staple)</b> -- real, existing vanilla food items, Appendix C's own tier
 *   classification, unchanged. Suspicious Stew is deliberately excluded (a player+flower
 *   interaction, not a real recipe -- nothing to strip or regenerate).</li>
 *   <li><b>Track B (signature dish)</b> -- 5 brand-new invented items, one per tier, forming a real
 *   chain (confirmed with the user 2026-10-03: each tier &ge;2 requires one or more of the previous
 *   tier's own dish as an ingredient, see {@code FoodRecipeGenerator}). Placeholder names/textures
 *   this pass, same "no new art needed" precedent as {@code rest.StoolBlock} reusing oak planks.</li>
 * </ul>
 *
 * <p>{@code slotCapacity}/grid sizes are Section 19.6's cooking-structure grid areas (1x1, 3x2, 5x3,
 * 6x4, 7x5), reinterpreted as an unordered ingredient-slot count, same idiom
 * {@code craft.EquipmentTierLadder} already uses for crafting structures.
 */
public final class FoodTierLadder {

    public static final int MIN_TIER = 1;
    public static final int MAX_TIER = 5;

    public static final Identifier ENCHANTED_GOLDEN_APPLE = Identifier.withDefaultNamespace("enchanted_golden_apple");
    public static final Identifier GOLDEN_APPLE = Identifier.withDefaultNamespace("golden_apple");

    private static final Map<Integer, List<Identifier>> TRACK_A = Map.of(
            1, idList("cooked_beef", "cooked_porkchop", "cooked_chicken", "cooked_mutton", "cooked_rabbit",
                    "cooked_cod", "cooked_salmon", "baked_potato", "bread", "cookie", "mushroom_stew"),
            2, idList("beetroot_soup", "pumpkin_pie", "golden_carrot"),
            3, idList("cake", "rabbit_stew"),
            4, idList("golden_apple"),
            5, idList("enchanted_golden_apple")
    );

    // Placeholder names/textures -- easy to retune, same convention as every other first-pass item
    // in this mod. Textures reuse existing vanilla food icons rather than new art (see ModFoodItems).
    private static final Map<Integer, Identifier> TRACK_B = Map.of(
            1, lyfeId("hearty_biscuit"),
            2, lyfeId("meat_loaf"),
            3, lyfeId("shepherds_platter"),
            4, lyfeId("banquet_plate"),
            5, lyfeId("feast_of_the_kyngdoms")
    );

    private FoodTierLadder() {
    }

    public static List<Identifier> trackA(int tier) {
        requireValidTier(tier);
        return TRACK_A.get(tier);
    }

    public static Identifier trackB(int tier) {
        requireValidTier(tier);
        return TRACK_B.get(tier);
    }

    public static int tierOf(Identifier resultId) {
        for (int tier = MIN_TIER; tier <= MAX_TIER; tier++) {
            if (trackA(tier).contains(resultId) || trackB(tier).equals(resultId)) {
                return tier;
            }
        }
        throw new IllegalArgumentException("Not a cooking-overhaul result id: " + resultId);
    }

    public static boolean isTrackB(Identifier resultId) {
        return TRACK_B.containsValue(resultId);
    }

    /** Every result id this slice generates a recipe for -- Track A's vanilla items plus Track B's 5 signature dishes. */
    public static List<Identifier> allResultIds() {
        List<Identifier> ids = new ArrayList<>();
        for (int tier = MIN_TIER; tier <= MAX_TIER; tier++) {
            ids.addAll(trackA(tier));
            ids.add(trackB(tier));
        }
        return List.copyOf(ids);
    }

    /** Section 19.6's cooking-structure grid areas (1x1/3x2/5x3/6x4/7x5), as an unordered slot count. */
    public static int slotCapacity(int tier) {
        requireValidTier(tier);
        return switch (tier) {
            case 1 -> 1;
            case 2 -> 6;
            case 3 -> 15;
            case 4 -> 24;
            case 5 -> 35;
            default -> throw new IllegalArgumentException("tier " + tier);
        };
    }

    public static int[] gridDims(int tier) {
        requireValidTier(tier);
        return switch (tier) {
            case 1 -> new int[]{1, 1};
            case 2 -> new int[]{3, 2};
            case 3 -> new int[]{5, 3};
            case 4 -> new int[]{6, 4};
            case 5 -> new int[]{7, 5};
            default -> throw new IllegalArgumentException("tier " + tier);
        };
    }

    private static List<Identifier> idList(String... names) {
        return List.of(names).stream().map(Identifier::withDefaultNamespace).toList();
    }

    private static Identifier lyfeId(String name) {
        return Identifier.fromNamespaceAndPath(LyfeMod.MODID, name);
    }

    private static void requireValidTier(int tier) {
        if (tier < MIN_TIER || tier > MAX_TIER) {
            throw new IllegalArgumentException("tier " + tier + " out of range [" + MIN_TIER + "," + MAX_TIER + "]");
        }
    }
}
