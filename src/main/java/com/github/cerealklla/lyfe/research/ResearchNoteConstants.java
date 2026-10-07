package com.github.cerealklla.lyfe.research;

import com.github.cerealklla.lyfe.craft.EquipmentTierLadder;

import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;

/**
 * First-pass tunable placeholders for loot-found Research Notes (2026-10-03, user request), same
 * convention as every other numeric constant in this mod.
 */
public final class ResearchNoteConstants {

    /** Chance the bonus Research Notes pool fires at all on a given chest open -- most opens add nothing extra. */
    public static final float POOL_CHANCE = 0.20F;

    /** Index 0 = Tier 1 .. index 4 = Tier 5 -- higher tier is rarer, matching vanilla's own weight idiom. */
    public static final int[] TIER_WEIGHTS = {40, 25, 15, 12, 8};

    private ResearchNoteConstants() {
    }

    /**
     * A random valid Research Note target for {@code tier} -- shared between {@code
     * loot.ResearchNoteLootInjector}'s own candidate loop (which iterates every tool deterministically)
     * and {@code fishing.FishermanListener}'s bonus-loot roll (which just needs one random pick),
     * added 2026-10-03 so both call sites agree on "what's a valid note target" rather than each
     * re-deriving it. Only ever called with {@code tier} in {@code [1, EquipmentTierLadder.MAX_REACHABLE_TIER]}.
     */
    public static Identifier randomTargetForTier(int tier, RandomSource random) {
        EquipmentTierLadder.ToolType[] tools = EquipmentTierLadder.ToolType.values();
        // Armor (added 2026-10-04) shares this same random pick -- a combined pool of every
        // ToolType plus every armor ArmorType, so Research Notes can target either equally.
        int totalOptions = tools.length + EquipmentTierLadder.ARMOR_TYPES.size();
        int roll = random.nextInt(totalOptions);
        if (roll < tools.length) {
            return EquipmentTierLadder.itemId(tools[roll], tier);
        }
        return EquipmentTierLadder.armorItemId(EquipmentTierLadder.ARMOR_TYPES.get(roll - tools.length), tier);
    }
}
