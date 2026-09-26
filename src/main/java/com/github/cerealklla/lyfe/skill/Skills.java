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
}
