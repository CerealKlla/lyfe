package com.github.cerealklla.lyfe.recallcinite;

import java.util.List;
import java.util.Locale;

import com.github.cerealklla.lyfe.skill.Skills;

/**
 * The Recallcraft skill's one passive perk: a cooldown-reduction fraction on the Recallcinite
 * Totem's bind/recall cooldown, up to {@link #MAX_COOLDOWN_REDUCTION} at {@link Skills#MAX_LEVEL} --
 * same {@code Math.min(cap, level/max*cap)} shape as {@code merchant.MerchantListener#bonusFraction}.
 * Applied multiplicatively on top of whichever base cooldown {@link RecallciniteCooldown} selected
 * (the plain 60-minute default, or the reduced Recallcinite-Stone-plot formula).
 */
public final class RecallcraftPerks {

    public static final double MAX_COOLDOWN_REDUCTION = 0.20;

    private RecallcraftPerks() {
    }

    public static double cooldownReductionFraction(int level) {
        return Math.min(MAX_COOLDOWN_REDUCTION, (double) level / Skills.MAX_LEVEL * MAX_COOLDOWN_REDUCTION);
    }

    /** Live benefit readout for the Skills screen (common.skill.SkillBenefits). */
    public static List<String> benefitLines(int level) {
        double percent = cooldownReductionFraction(level) * 100;
        return List.of("Recallcinite Totem cooldown: -" + String.format(Locale.ROOT, "%.1f", percent) + "%");
    }
}
