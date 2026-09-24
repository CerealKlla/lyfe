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
 */
public final class Skills {

    public static final SkillId LUMBERJACK_ID = new SkillId("lumberjack");
    public static final SkillId MINER_ID = new SkillId("miner");
    public static final SkillId SURVIVALIST_ID = new SkillId("survivalist");
    public static final SkillId COOK_ID = new SkillId("cook");

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
}
