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

    private Skills() {
    }

    public static void bootstrap() {
        SkillRegistry.register(lumberjack());
        SkillRegistry.register(miner());
    }

    // Placeholder curve, deliberately easy to retune (mirrors the design doc's own framing of the
    // Section 6 tier-unlock levels as "placeholder, easy to retune"). Lumberjack/Miner are meant to
    // level much faster than Historian (Section 3) — a shallow 50-level curve reflects that.
    private static XpCurve gatheringCurve() {
        List<Long> thresholds = new ArrayList<>();
        for (int level = 1; level <= 50; level++) {
            thresholds.add(Math.round(10 * Math.pow(level, 1.5)));
        }
        return new XpCurve(thresholds);
    }

    private static SkillDefinition lumberjack() {
        return new SkillDefinition(
                new SkillId("lumberjack"),
                "Lumberjack",
                SkillCategory.GATHERING,
                Optional.empty(),
                gatheringCurve(),
                List.of()
        );
    }

    private static SkillDefinition miner() {
        return new SkillDefinition(
                new SkillId("miner"),
                "Miner",
                SkillCategory.GATHERING,
                Optional.empty(),
                gatheringCurve(),
                List.of()
        );
    }
}
