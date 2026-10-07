package com.github.cerealklla.lyfe.reincarnation;

import java.util.Arrays;
import java.util.List;

/**
 * Pure logic for Reincarnation's level -> protected-slot mapping — no live-world dependency, same
 * shape as {@code skill.XpCurve} itself, so this is genuinely unit-testable.
 */
public final class ReincarnationSlots {

    /** Protection maxes out once every {@link ProtectedSlot} is unlocked. */
    public static final int MAX_LEVEL = ProtectedSlot.values().length;

    private ReincarnationSlots() {
    }

    /** Every slot protected at {@code level} — cumulative, not just the newest one. Clamped to {@link #MAX_LEVEL}. */
    public static List<ProtectedSlot> protectedSlotsForLevel(int level) {
        int count = Math.max(0, Math.min(level, MAX_LEVEL));
        return Arrays.asList(ProtectedSlot.values()).subList(0, count);
    }

    /**
     * Live benefit readout for the Skills screen (common.skill.SkillBenefits) -- only what's already
     * unlocked, plus the single next unlock, per the user's 2026-10-02 request (not the full 14-slot
     * table including everything still locked).
     */
    public static List<String> benefitLines(int level) {
        ProtectedSlot[] all = ProtectedSlot.values();
        int unlocked = Math.max(0, Math.min(level, MAX_LEVEL));
        List<String> lines = new java.util.ArrayList<>();
        for (int i = 0; i < unlocked; i++) {
            lines.add("Protected: " + displayName(all[i]));
        }
        if (unlocked == 0) {
            lines.add("Nothing protected yet");
        }
        if (unlocked < MAX_LEVEL) {
            lines.add("Next unlock (Level " + (unlocked + 1) + "): " + displayName(all[unlocked]));
        }
        return lines;
    }

    private static String displayName(ProtectedSlot slot) {
        return switch (slot) {
            case ARMOR_HEAD -> "Helmet";
            case ARMOR_CHEST -> "Chestplate";
            case ARMOR_LEGS -> "Leggings";
            case ARMOR_FEET -> "Boots";
            case OFFHAND -> "Offhand";
            default -> "Hotbar slot " + (slot.hotbarIndex() + 1);
        };
    }
}
