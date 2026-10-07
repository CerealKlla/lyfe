package com.github.cerealklla.lyfe.skill;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Bootstraps the skill definitions that exist so far. Lumberjack and Miner (design doc Section 6)
 * are the confirmed first implementation milestone (Appendix B) — registered here as data now that
 * the core skill model (Phase 1) exists. Their effects list is deliberately empty for now: the
 * Section 6 tool-tier gates need a real mapping onto vanilla tool material tiers, which is Phase 2
 * work (verified against the decompiled Tiers API alongside the actual block-break event hooks),
 * not invented here ahead of that.
 *
 * <p>Cartographyr and Historian (design doc Section 9) were added 2026-09-24 once Cartographyr
 * gained real settlement data to hang the sign/map mechanic on. Both are gated behind {@code
 * requiredModId = Optional.of("cartographyr")} (Section 8) so they're invisible, not just locked,
 * when Cartographyr isn't loaded. Historian's effects list stays empty because it has no
 * XP-granting mechanic yet at all -- see decisions.md.
 */
public final class Skills {

    public static final SkillId LUMBERJACK_ID = new SkillId("lumberjack");
    public static final SkillId MINER_ID = new SkillId("miner");
    public static final SkillId SURVIVALIST_ID = new SkillId("survivalist");
    public static final SkillId COOK_ID = new SkillId("cook");
    public static final SkillId CARTOGRAPHYR_ID = new SkillId("cartographyr");
    public static final SkillId HISTORIAN_ID = new SkillId("historian");
    public static final SkillId MERCHANT_ID = new SkillId("merchant");
    public static final SkillId REINCARNATION_ID = new SkillId("reincarnation");
    public static final SkillId SWIMMER_ID = new SkillId("swimmer");
    public static final SkillId RESEARCHER_ID = new SkillId("researcher");
    public static final SkillId CRAFTER_ID = new SkillId("crafter");
    public static final SkillId HEARTINESS_ID = new SkillId("heartiness");
    public static final SkillId FISHERMAN_ID = new SkillId("fisherman");
    public static final SkillId FARMER_ID = new SkillId("farmer");
    public static final SkillId SWORDSMAN_ID = new SkillId("swordsman");
    public static final SkillId AXEMAN_ID = new SkillId("axeman");
    public static final SkillId PIKEMAN_ID = new SkillId("pikeman");
    public static final SkillId EXCAVATOR_ID = new SkillId("excavator");

    // Shared by every curve below; also the level Survivalist's true-hunger-max scaling (see
    // .hunger.HungerListener) treats as "max level" when computing capacity growth.
    public static final int MAX_LEVEL = 50;

    private Skills() {
    }

    public static void bootstrap() {
        SkillRegistry.register(lumberjack());
        SkillRegistry.register(miner());
        SkillRegistry.register(survivalist());
        SkillRegistry.register(cook());
        SkillRegistry.register(cartographyr());
        SkillRegistry.register(historian());
        SkillRegistry.register(merchant());
        SkillRegistry.register(reincarnation());
        SkillRegistry.register(swimmer());
        SkillRegistry.register(researcher());
        SkillRegistry.register(crafter());
        SkillRegistry.register(heartiness());
        SkillRegistry.register(fisherman());
        SkillRegistry.register(farmer());
        SkillRegistry.register(swordsman());
        SkillRegistry.register(axeman());
        SkillRegistry.register(pikeman());
        SkillRegistry.register(excavator());
    }

    // Placeholder curve, deliberately easy to retune (mirrors the design doc's own framing of the
    // Section 6 tier-unlock levels as "placeholder, easy to retune"). Lumberjack/Miner/Survivalist/
    // Cook are meant to level much faster than Historian (Section 3) — a shallow curve reflects that.
    private static XpCurve fastCurve() {
        List<Long> thresholds = new ArrayList<>();
        for (int level = 1; level <= MAX_LEVEL; level++) {
            thresholds.add(Math.round(10 * Math.pow(level, 1.5)));
        }
        return new XpCurve(thresholds);
    }

    private static SkillDefinition lumberjack() {
        return new SkillDefinition(
                LUMBERJACK_ID,
                "Lumberjack",
                SkillCategory.GATHERING,
                Optional.empty(),
                fastCurve(),
                List.of()
        );
    }

    private static SkillDefinition miner() {
        return new SkillDefinition(
                MINER_ID,
                "Miner",
                SkillCategory.GATHERING,
                Optional.empty(),
                fastCurve(),
                List.of()
        );
    }

    // Effects lists are empty for the same reason Lumberjack/Miner's are: the mechanics (Section 10)
    // are computed directly by .hunger.HungerListener rather than by consulting SkillEffect data,
    // matching the established precedent (SPEED_MULTIPLIER etc. are also plain formulas, not
    // SkillEffect-driven) rather than inventing a new EffectType kind for a single use.
    private static SkillDefinition survivalist() {
        return new SkillDefinition(
                SURVIVALIST_ID,
                "Survivalist",
                SkillCategory.SURVIVAL_CRAFT,
                Optional.empty(),
                fastCurve(),
                List.of()
        );
    }

    private static SkillDefinition cook() {
        return new SkillDefinition(
                COOK_ID,
                "Cook",
                SkillCategory.SURVIVAL_CRAFT,
                Optional.empty(),
                fastCurve(),
                List.of()
        );
    }

    // Deliberately steeper than fastCurve() -- design doc Section 3 explicitly calls for Historian
    // to level much slower than Lumberjack/Miner. Placeholder, like fastCurve() itself.
    private static XpCurve slowCurve() {
        List<Long> thresholds = new ArrayList<>();
        for (int level = 1; level <= MAX_LEVEL; level++) {
            thresholds.add(Math.round(40 * Math.pow(level, 1.8)));
        }
        return new XpCurve(thresholds);
    }

    // Cartographyr's own curve, 2026-09-25 (see decisions.md) -- deliberately a short 10-level
    // scale, not the shared 1-50 MAX_LEVEL every other skill uses (XpCurve#maxLevel is just its own
    // threshold list's size, so this is independent of MAX_LEVEL entirely). No longer shared with
    // Historian's slowCurve() -- Historian still has no XP-granting mechanic at all, so its own
    // rate remains an open question for whenever that's built.
    private static final int CARTOGRAPHYR_MAX_LEVEL = 10;

    private static XpCurve cartographyrCurve() {
        List<Long> thresholds = new ArrayList<>();
        for (int level = 1; level <= CARTOGRAPHYR_MAX_LEVEL; level++) {
            thresholds.add(Math.round(50 * Math.pow(level, 1.8)));
        }
        return new XpCurve(thresholds);
    }

    // Effects lists are empty: no perks exist yet for either skill (the sign/map mechanic grants
    // XP directly, not via SkillEffect; Historian has no XP-granting mechanic at all yet -- see
    // decisions.md, both gated behind requiredModId since neither means anything without
    // Cartographyr's data).
    private static SkillDefinition cartographyr() {
        return new SkillDefinition(
                CARTOGRAPHYR_ID,
                "Cartographyr",
                SkillCategory.KNOWLEDGE,
                Optional.of("cartographyr"),
                cartographyrCurve(),
                List.of()
        );
    }

    private static SkillDefinition historian() {
        return new SkillDefinition(
                HISTORIAN_ID,
                "Historian",
                SkillCategory.KNOWLEDGE,
                Optional.of("cartographyr"),
                slowCurve(),
                List.of()
        );
    }

    // Gated on Yconomics (design doc / decisions.md, 2026-09-26) -- the whole point of this skill
    // is Coin Purse tier integration, so unlike Cartographyr/Historian there's no meaningful
    // "standalone, just hide the Yconomics-specific parts" mode worth preserving; the skill is
    // simply invisible without Yconomics loaded, same requiredModId-gating shape as Cartographyr's
    // own dependency-gated skills. Effects list empty for the same reason every other skill's is --
    // XP/price-bonus/tier logic is computed directly in .merchant.MerchantListener, not driven by
    // SkillEffect data.
    private static SkillDefinition merchant() {
        return new SkillDefinition(
                MERCHANT_ID,
                "Merchant",
                SkillCategory.SOCIAL,
                Optional.of("yconomics"),
                fastCurve(),
                List.of()
        );
    }

    // Deliberately short (reincarnation.ReincarnationSlots.MAX_LEVEL levels, not the shared
    // MAX_LEVEL=50 every other skill uses) -- protection fully maxes out once every slot is
    // unlocked, so levels beyond that would mean nothing. Placeholder numbers, flagged as tunable
    // like every other curve here: ~50 XP/death (one death = this curve's own level-1 threshold),
    // scaled so the final level lands around 3000-3500 total XP (~60-70 deaths to fully max out) --
    // slow enough that full protection is a real achievement, not trivial.
    private static XpCurve reincarnationCurve() {
        List<Long> thresholds = new ArrayList<>();
        for (int level = 1; level <= com.github.cerealklla.lyfe.reincarnation.ReincarnationSlots.MAX_LEVEL; level++) {
            thresholds.add(Math.round(80 * Math.pow(level, 1.6)));
        }
        return new XpCurve(thresholds);
    }

    // Effects list empty, same reason every other skill's is -- the slot-protection logic is
    // computed directly by reincarnation.ReincarnationListener reading Lyfe.getLevel, not driven by
    // SkillEffect data. No requiredModId -- no cross-mod dependency.
    private static SkillDefinition reincarnation() {
        return new SkillDefinition(
                REINCARNATION_ID,
                "Reincarnation",
                SkillCategory.COMBAT,
                Optional.empty(),
                reincarnationCurve(),
                List.of()
        );
    }

    // Effects list empty, same reason every other skill's is -- capacity/recovery/swim-speed scaling
    // is computed directly by swim.SwimmerListener reading Lyfe.getLevel, not driven by SkillEffect
    // data. No requiredModId -- no cross-mod dependency (design doc Section 5, Exploration category).
    private static SkillDefinition swimmer() {
        return new SkillDefinition(
                SWIMMER_ID,
                "Swimmer",
                SkillCategory.EXPLORATION,
                Optional.empty(),
                fastCurve(),
                List.of()
        );
    }

    // Effects list empty, same reason every other skill's is -- durability-save/crit-fail/crit-success
    // odds (design doc Section 19.1) are computed directly by research.ResearchListener reading
    // Lyfe.getLevel, not driven by SkillEffect data. No requiredModId (Section 19, no mod dependency).
    private static SkillDefinition researcher() {
        return new SkillDefinition(
                RESEARCHER_ID,
                "Researcher",
                SkillCategory.SURVIVAL_CRAFT,
                Optional.empty(),
                fastCurve(),
                List.of()
        );
    }

    // Effects list empty, same reason every other skill's is -- component-saving chance, Quality
    // score, and tier-unlock gating (design doc Section 19.2) are computed directly by
    // craft.CraftingStructureMenu reading Lyfe.getLevel, not driven by SkillEffect data. No
    // requiredModId (Section 19, no mod dependency).
    private static SkillDefinition crafter() {
        return new SkillDefinition(
                CRAFTER_ID,
                "Crafter",
                SkillCategory.SURVIVAL_CRAFT,
                Optional.empty(),
                fastCurve(),
                List.of()
        );
    }

    // Own curve, 5x fastCurve()'s coefficient (2026-10-03 playtest correction -- the shared
    // fastCurve() made Heartiness trivially easy to farm just by taking and recovering from chip
    // damage), then +80% on top of that (2026-10-05, explicit user request: 50 -> 90) to slow
    // leveling further. Effects list empty, same reason every other skill's is -- bonus max health,
    // the post-level-25 Rapid Recovery proc chance, and the damage-bank/XP-on-recovery mechanic are
    // all computed directly by heartiness.HeartinessListener reading Lyfe.getLevel, not driven by
    // SkillEffect data. No requiredModId -- no cross-mod dependency.
    private static XpCurve heartinessCurve() {
        List<Long> thresholds = new ArrayList<>();
        for (int level = 1; level <= MAX_LEVEL; level++) {
            thresholds.add(Math.round(90 * Math.pow(level, 1.5)));
        }
        return new XpCurve(thresholds);
    }

    private static SkillDefinition heartiness() {
        return new SkillDefinition(
                HEARTINESS_ID,
                "Heartiness",
                SkillCategory.COMBAT,
                Optional.empty(),
                heartinessCurve(),
                List.of()
        );
    }

    // Effects list empty, same reason every other skill's is -- bonus-loot chance, the lucky-spot
    // bonus, Sunken Treasure chance, and the custom Nether/End catch loop (design doc, 2026-10-03
    // user request) are all computed directly by fishing.FishermanListener reading Lyfe.getLevel,
    // not driven by SkillEffect data. Reuses fastCurve() -- catching fish is a frequent, low-effort
    // action, same bar as Swimmer. No requiredModId -- no cross-mod dependency.
    private static SkillDefinition fisherman() {
        return new SkillDefinition(
                FISHERMAN_ID,
                "Fisherman",
                SkillCategory.EXPLORATION,
                Optional.empty(),
                fastCurve(),
                List.of()
        );
    }

    // Effects list empty, same reason every other skill's is -- bonus-seed chance, the hoe-leaf
    // rare-drop boost, crop-yield doubling, and bonemeal-save chance (design doc Section 23,
    // 2026-10-04 user request) are all computed directly by farming.FarmerListener reading
    // Lyfe.getLevel, not driven by SkillEffect data. Reuses fastCurve() -- harvesting crops is a
    // frequent, low-effort action, same bar as Swimmer/Fisherman. No requiredModId -- no cross-mod
    // dependency.
    private static SkillDefinition farmer() {
        return new SkillDefinition(
                FARMER_ID,
                "Farmer",
                SkillCategory.GATHERING,
                Optional.empty(),
                fastCurve(),
                List.of()
        );
    }

    // Swordsman/Axeman/Pikeman (2026-10-06, user request) -- the combat-context counterparts the
    // design doc flagged as "future, not designed" (Section 11, Appendix). Each owns gating for
    // one EquipmentTierLadder.ToolType (Sword/Axe/Spear) via the shared equip-tier-warning/
    // extra-durability mechanic (see craft.EquipmentTierLadder#governingSkillId,
    // craft.ProficiencyDurabilityListener) -- Axeman is deliberately separate from Lumberjack's
    // existing gathering-context Axe gate (gathering.GatheringListener, unchanged -- reads the
    // same craft.ToolTierUnlocks table), per the user's own explicit split.
    // XP granted on dealing melee damage with the matching weapon (combat.CombatSkillListener).
    // Effects lists empty, same reason every other skill's is -- no passive bonuses designed yet,
    // this pass is scoped to tier-gating only.
    private static SkillDefinition swordsman() {
        return new SkillDefinition(
                SWORDSMAN_ID,
                "Swordsman",
                SkillCategory.COMBAT,
                Optional.empty(),
                meleeCombatCurve(),
                List.of()
        );
    }

    private static SkillDefinition axeman() {
        return new SkillDefinition(
                AXEMAN_ID,
                "Axeman",
                SkillCategory.COMBAT,
                Optional.empty(),
                meleeCombatCurve(),
                List.of()
        );
    }

    private static SkillDefinition pikeman() {
        return new SkillDefinition(
                PIKEMAN_ID,
                "Pikeman",
                SkillCategory.COMBAT,
                Optional.empty(),
                meleeCombatCurve(),
                List.of()
        );
    }

    // Own curve, 10x fastCurve()'s coefficient (2026-10-06, same day as CombatSkillListener's own
    // MOB_DAMAGE_XP_MULTIPLIER=10 -- mob kills are the expected primary XP source for these three,
    // so the curve is scaled up by the same factor to keep real leveling pace in line with every
    // other fastCurve() skill, rather than reaching max level ~10x faster than intended). Explicit
    // user request: "I don't want someone able to get max level in a few hours."
    private static XpCurve meleeCombatCurve() {
        List<Long> thresholds = new ArrayList<>();
        for (int level = 1; level <= MAX_LEVEL; level++) {
            thresholds.add(Math.round(100 * Math.pow(level, 1.5)));
        }
        return new XpCurve(thresholds);
    }

    // Excavator (2026-10-06, user request) -- owns gating for ToolType.SHOVEL. XP granted on
    // breaking a shovel-appropriate block with a Shovel (gathering.ExcavatorListener), mirroring
    // Farmer/Lumberjack/Miner's own gathering-context XP shape exactly.
    private static SkillDefinition excavator() {
        return new SkillDefinition(
                EXCAVATOR_ID,
                "Excavator",
                SkillCategory.GATHERING,
                Optional.empty(),
                fastCurve(),
                List.of()
        );
    }
}
