package com.github.cerealklla.lyfe.durability;

/**
 * Pure tier/percentage math for the Equipment Status HUD panel (2026-10-07 user request). Separate
 * from {@link UnbreakableConstants#isAtZero}, which only answers "has this clamped all the way" --
 * this answers "how close is it," for display purposes. Deliberately takes plain ints rather than an
 * {@code ItemStack} so it's genuinely unit-testable (constructing a real {@code ItemStack} in this
 * project's plain JUnit setup throws "Components not bound yet" -- a known limitation, see
 * Yconomics' CLAUDE.md).
 */
public final class DurabilityStatus {

    private static final double GREY_THRESHOLD_PERCENT = 25.0;
    private static final double BAND_THIRD = GREY_THRESHOLD_PERCENT / 3.0;

    private DurabilityStatus() {
    }

    public enum Tier {
        GREY(0xFF888888),
        YELLOW(0xFFFFD700),
        ORANGE(0xFFFF8C00),
        RED(0xFFFF3030);

        public final int tintColor;

        Tier(int tintColor) {
            this.tintColor = tintColor;
        }
    }

    public static double percentRemaining(int damageValue, int maxDamage) {
        if (maxDamage <= 0) {
            return 100.0;
        }
        return 100.0 * (maxDamage - damageValue) / maxDamage;
    }

    public static Tier tierFor(int damageValue, int maxDamage) {
        double percent = percentRemaining(damageValue, maxDamage);
        if (percent > GREY_THRESHOLD_PERCENT) {
            return Tier.GREY;
        }
        if (percent > GREY_THRESHOLD_PERCENT - BAND_THIRD) {
            return Tier.YELLOW;
        }
        if (percent > GREY_THRESHOLD_PERCENT - 2 * BAND_THIRD) {
            return Tier.ORANGE;
        }
        return Tier.RED;
    }
}
