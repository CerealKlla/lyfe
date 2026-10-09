package com.github.cerealklla.lyfe.skill;

import java.util.List;

import com.github.cerealklla.lyfe.craft.CrafterConstants;
import com.github.cerealklla.lyfe.craft.ToolTierUnlocks;
import com.github.cerealklla.lyfe.expeditionist.ExpeditionistConstants;
import com.github.cerealklla.lyfe.farming.FarmerListener;
import com.github.cerealklla.lyfe.gathering.GatheringListener;
import com.github.cerealklla.lyfe.cook.CookingListener;
import com.github.cerealklla.lyfe.fishing.FishermanListener;
import com.github.cerealklla.lyfe.heartiness.HeartinessListener;
import com.github.cerealklla.lyfe.gathering.GatheringSkill;
import com.github.cerealklla.lyfe.hunger.HungerListener;
import com.github.cerealklla.lyfe.knowledge.SignListener;
import com.github.cerealklla.lyfe.merchant.MerchantListener;
import com.github.cerealklla.lyfe.reincarnation.ReincarnationSlots;
import com.github.cerealklla.lyfe.research.ResearcherConstants;
import com.github.cerealklla.lyfe.swim.SwimmerListener;

import net.minecraft.world.entity.player.Player;

/**
 * Dispatches to each skill's own "describe my current benefits" method for the Skills screen
 * (design doc Section 13) -- every formula stays co-located with its real implementation (the
 * listener/constants class that actually computes it), this is just the single switch that routes
 * a {@link SkillId} to the right one, so there is exactly one source of truth per formula instead of
 * a parallel copy here.
 */
public final class SkillBenefits {

    private SkillBenefits() {
    }

    /**
     * Current, already-computed values only (e.g. "Break speed +13%") -- never the underlying formula.
     * {@code player} may be {@code null} for every skill except Merchant, which needs it to read the
     * player's *real* Coin Purse tier from Yconomics directly (its auto-tier formula alone isn't
     * trustworthy to display, since Yconomics only ever raises the real tier on an actual completed
     * trade -- see {@code MerchantListener#onTrade} -- so it can legitimately lag behind what the
     * formula alone would predict from the player's current Merchant level).
     */
    public static List<String> describe(SkillId id, Player player, int level, int maxLevel) {
        if (id.equals(Skills.LUMBERJACK_ID)) {
            return GatheringListener.benefitLines(GatheringSkill.LUMBERJACK, level);
        }
        if (id.equals(Skills.MINER_ID)) {
            return GatheringListener.benefitLines(GatheringSkill.MINER, level);
        }
        if (id.equals(Skills.SURVIVALIST_ID)) {
            return HungerListener.survivalistBenefitLines(level);
        }
        if (id.equals(Skills.COOK_ID)) {
            return CookingListener.benefitLines(level);
        }
        if (id.equals(Skills.CARTOGRAPHYR_ID)) {
            return SignListener.cartographyrBenefitLines(level);
        }
        if (id.equals(Skills.HISTORIAN_ID)) {
            return List.of("No bonuses yet -- coming soon");
        }
        if (id.equals(Skills.MERCHANT_ID)) {
            return MerchantListener.benefitLines(player, level);
        }
        if (id.equals(Skills.REINCARNATION_ID)) {
            return ReincarnationSlots.benefitLines(level);
        }
        if (id.equals(Skills.SWIMMER_ID)) {
            return SwimmerListener.benefitLines(level);
        }
        if (id.equals(Skills.RESEARCHER_ID)) {
            return ResearcherConstants.benefitLines(level, maxLevel);
        }
        if (id.equals(Skills.CRAFTER_ID)) {
            return CrafterConstants.benefitLines(level, maxLevel);
        }
        if (id.equals(Skills.HEARTINESS_ID)) {
            return HeartinessListener.benefitLines(level);
        }
        if (id.equals(Skills.FISHERMAN_ID)) {
            return FishermanListener.benefitLines(level);
        }
        if (id.equals(Skills.FARMER_ID)) {
            return FarmerListener.benefitLines(level);
        }
        // Swordsman/Axeman/Pikeman/Excavator (2026-10-06) -- no passive bonuses designed yet, just
        // the tier-unlock readout every tool-tier-gated skill shows (same table every other gated
        // skill reads, see ToolTierUnlocks).
        if (id.equals(Skills.SWORDSMAN_ID)) {
            return List.of("Unlocked Sword tier: " + ToolTierUnlocks.unlockedTierName(level));
        }
        if (id.equals(Skills.AXEMAN_ID)) {
            return List.of("Unlocked Axe (combat) tier: " + ToolTierUnlocks.unlockedTierName(level));
        }
        if (id.equals(Skills.PIKEMAN_ID)) {
            return List.of("Unlocked Spear tier: " + ToolTierUnlocks.unlockedTierName(level));
        }
        if (id.equals(Skills.EXCAVATOR_ID)) {
            return List.of("Unlocked Shovel tier: " + ToolTierUnlocks.unlockedTierName(level));
        }
        if (id.equals(Skills.EXPEDITIONIST_ID)) {
            return ExpeditionistConstants.benefitLines(level);
        }
        if (id.equals(Skills.MAYOR_ID)) {
            return com.github.cerealklla.lyfe.mayor.MayorConstants.benefitLines(level);
        }
        return List.of();
    }
}
