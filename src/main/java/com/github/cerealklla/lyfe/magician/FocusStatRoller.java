package com.github.cerealklla.lyfe.magician;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import com.github.cerealklla.lyfe.craft.FocusLadder;

/**
 * Pure craft-time rolling logic for a Focus (design doc Section 25) -- called once per craft from
 * {@code craft.CraftingStructureMenu#attemptCraft}, same injection point {@code QualityApplier}/
 * {@code NatureStaffItem#attributesFor} already use.
 */
public final class FocusStatRoller {

    /** 25% at Tier 3+ (explicit user spec gave no number -- flagged tunable placeholder, same convention as every other untuned magnitude in this mod). */
    public static final double SPELL_GRANT_CHANCE = 0.25;
    private static final int MIN_SPELL_TIER = 3;

    private FocusStatRoller() {
    }

    /** Linear 1 -> 3 across Tier 2..5 (explicit spec: "Number of passive stats 1 -> 3"). */
    public static int statCountForTier(int tier) {
        double fraction = (Math.max(FocusLadder.MIN_TIER, Math.min(tier, FocusLadder.MAX_TIER)) - FocusLadder.MIN_TIER)
                / (double) (FocusLadder.MAX_TIER - FocusLadder.MIN_TIER);
        return (int) Math.round(1 + 2 * fraction);
    }

    /** {@code statCountForTier(tier)} distinct stats, each rolled within its own tier-interpolated range. */
    public static List<FocusData.RolledStat> rollStats(int tier, Random random) {
        List<FocusStatType> pool = new ArrayList<>(List.of(FocusStatType.values()));
        java.util.Collections.shuffle(pool, random);
        int count = Math.min(statCountForTier(tier), pool.size());
        List<FocusData.RolledStat> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            FocusStatType type = pool.get(i);
            result.add(new FocusData.RolledStat(type, type.roll(tier, random)));
        }
        return result;
    }

    /** Tier 3+ only, {@link #SPELL_GRANT_CHANCE} chance, uniform over every known spell. */
    public static Optional<net.minecraft.resources.Identifier> rollSpell(int tier, Random random) {
        if (tier < MIN_SPELL_TIER || random.nextDouble() >= SPELL_GRANT_CHANCE) {
            return Optional.empty();
        }
        List<net.minecraft.resources.Identifier> spells = SpellIds.ALL;
        return Optional.of(spells.get(random.nextInt(spells.size())));
    }

    public static FocusVariant rollVariant(Random random) {
        FocusVariant[] variants = FocusVariant.values();
        return variants[random.nextInt(variants.length)];
    }

    public static FocusData roll(int tier, Random random) {
        return new FocusData(rollVariant(random), rollStats(tier, random), rollSpell(tier, random));
    }
}
