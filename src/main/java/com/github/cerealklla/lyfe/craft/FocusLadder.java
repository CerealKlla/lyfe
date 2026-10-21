package com.github.cerealklla.lyfe.craft;

import java.util.List;
import java.util.Map;

import com.github.cerealklla.lyfe.LyfeMod;

import net.minecraft.resources.Identifier;

/**
 * The 4-tier Focus ladder (design doc Section 25, explicit user spec: "From Tier 2 (Stone) to
 * Tier 5"). Deliberately its own small class, same reasoning as {@link NatureStaffLadder} -- no
 * vanilla "focus" item exists at any material, so this needs brand-new custom {@code Item}s
 * registered in {@code registration.ModItems}, not an {@code EquipmentTierLadder.ToolType} entry.
 *
 * <p>Mapped onto Tiers 2-5 (Stone/Iron/Gold/Diamond) -- Tier 0/1 (Wood/Copper) and Tier 6
 * (Netherite) are skipped entirely, matching the spec's own tier range exactly. The tier *number*
 * is independent of this suite's shared material-progression numbering (where Tier 1 = Stone,
 * Tier 2 = Copper) -- same precedent as Bow (Tier 1)/Crossbow (Tier 3), whose own fixed tiers
 * don't line up with their real material either. Unlike Nature Staff, Focus has no melee stats at
 * all (a pure off-hand utility item, not a weapon), so this ladder carries only display names, no
 * {@code ToolMaterial}/attack-damage data.
 */
public final class FocusLadder {

    public static final int MIN_TIER = 2;
    public static final int MAX_TIER = 5;

    private static final Map<Integer, String> TIER_DISPLAY_NAMES = Map.of(
            2, "Stone", 3, "Iron", 4, "Gold", 5, "Diamond"
    );

    private FocusLadder() {
    }

    public static Identifier itemId(int tier) {
        requireValidTier(tier);
        return Identifier.fromNamespaceAndPath(LyfeMod.MODID, "focus_t" + tier);
    }

    public static Identifier previousTierItem(int tier) {
        requireValidTier(tier);
        return tier == MIN_TIER ? null : itemId(tier - 1);
    }

    public static List<Identifier> allIds() {
        return List.of(itemId(2), itemId(3), itemId(4), itemId(5));
    }

    public static int tierOfItem(Identifier itemId) {
        for (int tier = MIN_TIER; tier <= MAX_TIER; tier++) {
            if (itemId(tier).equals(itemId)) {
                return tier;
            }
        }
        return -1;
    }

    public static String tierDisplayName(int tier) {
        requireValidTier(tier);
        return TIER_DISPLAY_NAMES.get(tier);
    }

    private static void requireValidTier(int tier) {
        if (tier < MIN_TIER || tier > MAX_TIER) {
            throw new IllegalArgumentException("Focus tier " + tier + " out of range [" + MIN_TIER + "," + MAX_TIER + "]");
        }
    }
}
