package com.github.cerealklla.lyfe.socket;

import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * The Socketeer skill's real formulas (design: explicit user spec) -- kept here, not in {@code
 * skill.Skills}, same "the real implementation class owns its own formula" convention every other
 * skill in this mod already follows (see {@code skill.SkillBenefits}' own class doc).
 *
 * <p><b>Socketing progress/difficulty</b>: "an escalating xp limit; each socket requiring 2x the
 * previous effort" -- {@link #socketRequirement} is {@code 100 * 2^socketIndex} (socket 1 = 100,
 * socket 5 = 1600), a first-pass placeholder magnitude, flagged tunable like every other numeric
 * constant in this mod. Each successful attempt grants a flat {@link #PROGRESS_PER_SUCCESS} (20)
 * regardless of which socket is being worked -- only the *requirement* escalates, not the per-
 * attempt reward, which is what actually makes each socket take more attempts than the last.
 *
 * <p><b>Outcome rolling</b>: the user's own four socketing percentages (1-10/59-78/30-10/9-2) sum to
 * 99% at level 0 and 100% at level 50 -- a minor given-data inconsistency, resolved here by rolling
 * against the real (possibly not-quite-100) sum of the four interpolated weights directly, rather
 * than force-normalizing to exactly 100 first. The practical effect at level 0 is a roll universe of
 * 99 instead of 100 -- functionally identical to within noise, and avoids silently second-guessing
 * which of the four numbers the user actually meant to adjust. The three extracting percentages
 * (50-85/30-10/20-5) already sum to 100 at both ends, so this only ever matters for socketing.
 */
public final class SocketeerConstants {

    private SocketeerConstants() {
    }

    public static final int MAX_LEVEL = 50;
    public static final int DURABILITY_PER_ATTEMPT = 20;
    public static final int PROGRESS_PER_SUCCESS = 20;
    public static final int EXTRACT_DURABILITY_COST = 20;

    /** Progress points needed to complete the socket at {@code socketIndex} (0 = the 1st socket ever drilled on this item). */
    public static int socketRequirement(int socketIndex) {
        return 100 * (1 << Math.max(0, socketIndex));
    }

    public enum SocketOutcome {
        PROGRESS_HALF_DAMAGE, PROGRESS_FULL_DAMAGE, NO_PROGRESS_FULL_DAMAGE, NO_PROGRESS_DOUBLE_DAMAGE
    }

    public enum ExtractOutcome {
        SUCCESS, DEGRADE, DESTROY
    }

    public static int durabilityCostFor(SocketOutcome outcome) {
        return switch (outcome) {
            case PROGRESS_HALF_DAMAGE -> DURABILITY_PER_ATTEMPT / 2;
            case PROGRESS_FULL_DAMAGE, NO_PROGRESS_FULL_DAMAGE -> DURABILITY_PER_ATTEMPT;
            case NO_PROGRESS_DOUBLE_DAMAGE -> DURABILITY_PER_ATTEMPT * 2;
        };
    }

    public static int progressGrantedFor(SocketOutcome outcome) {
        return outcome == SocketOutcome.PROGRESS_HALF_DAMAGE || outcome == SocketOutcome.PROGRESS_FULL_DAMAGE
                ? PROGRESS_PER_SUCCESS : 0;
    }

    public static SocketOutcome rollSocketOutcome(int socketeerLevel, Random random) {
        double a = interp(socketeerLevel, 1, 10);
        double b = interp(socketeerLevel, 59, 78);
        double c = interp(socketeerLevel, 30, 10);
        double d = interp(socketeerLevel, 9, 2);
        double total = a + b + c + d;
        double roll = random.nextDouble() * total;
        if ((roll -= a) < 0) {
            return SocketOutcome.PROGRESS_HALF_DAMAGE;
        }
        if ((roll -= b) < 0) {
            return SocketOutcome.PROGRESS_FULL_DAMAGE;
        }
        if ((roll -= c) < 0) {
            return SocketOutcome.NO_PROGRESS_FULL_DAMAGE;
        }
        return SocketOutcome.NO_PROGRESS_DOUBLE_DAMAGE;
    }

    /**
     * {@code isRune}: "B cannot be rolled for Runes" (explicit user spec) -- Degrade only applies to
     * a Gem's quality, which a Rune has none of. B's own weight share is simply excluded from the
     * roll universe for a Rune (A and C's relative weights stay in the same ratio to each other,
     * just scaled up to fill the gap B would have occupied) rather than redistributed by some other
     * rule the user didn't specify.
     */
    public static ExtractOutcome rollExtractOutcome(int socketeerLevel, boolean isRune, Random random) {
        double a = interp(socketeerLevel, 50, 85);
        double b = isRune ? 0 : interp(socketeerLevel, 30, 10);
        double c = interp(socketeerLevel, 20, 5);
        double total = a + b + c;
        double roll = random.nextDouble() * total;
        if ((roll -= a) < 0) {
            return ExtractOutcome.SUCCESS;
        }
        if ((roll -= b) < 0) {
            return ExtractOutcome.DEGRADE;
        }
        return ExtractOutcome.DESTROY;
    }

    private static double interp(int level, double from, double to) {
        double t = Math.min(Math.max(level, 0), MAX_LEVEL) / (double) MAX_LEVEL;
        return from + (to - from) * t;
    }

    /** Live benefit readout for the Skills screen. */
    public static List<String> benefitLines(int level) {
        double a = interp(level, 1, 10);
        double b = interp(level, 59, 78);
        double c = interp(level, 30, 10);
        double d = interp(level, 9, 2);
        double total = a + b + c + d;
        return List.of(
                String.format(Locale.ROOT, "Socketing: %.0f%% full progress, %.0f%% progress, %.0f%% no progress, %.0f%% no progress (2x damage)",
                        100 * a / total, 100 * b / total, 100 * c / total, 100 * d / total),
                String.format(Locale.ROOT, "Extracting (Gems): %.0f%% success, %.0f%% degrade, %.0f%% destroy",
                        100 * interp(level, 50, 85) / (interp(level, 50, 85) + interp(level, 30, 10) + interp(level, 20, 5)),
                        100 * interp(level, 30, 10) / (interp(level, 50, 85) + interp(level, 30, 10) + interp(level, 20, 5)),
                        100 * interp(level, 20, 5) / (interp(level, 50, 85) + interp(level, 30, 10) + interp(level, 20, 5)))
        );
    }
}
