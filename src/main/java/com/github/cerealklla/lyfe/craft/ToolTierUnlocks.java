package com.github.cerealklla.lyfe.craft;

/**
 * Section 7's tool-tier gating mechanic, finally implemented 2026-10-04 now that it's unblocked --
 * Section 7 was explicitly deferred until Section 19.2's custom {@code EquipmentTierLadder} existed
 * to gate against (confirmed live since 2026-10-02), rather than inventing a throwaway gate against
 * vanilla's own material tiers as a stand-in.
 *
 * <p><b>Moved here from {@code gathering} and widened to every {@link EquipmentTierLadder.ToolType},
 * 2026-10-06</b> (user request) -- originally scoped to Axe/Lumberjack and Pickaxe/Miner only (the
 * two tool types with a real owning skill at the time), since Sword/Spear/Shovel/Hoe had no owning
 * skill at all. That gap is now closed: Swordsman/Axeman/Pikeman/Excavator (combat.
 * CombatSkillListener, gathering.ExcavatorListener) and the pre-existing Farmer now cover every
 * remaining {@code ToolType}, mapped via {@code EquipmentTierLadder#governingSkillId}.
 *
 * <p><b>Reworked from a hard speed/equip block into an extra-durability-damage penalty, same day,
 * second round</b> -- the user changed their mind mid-session: being under-leveled no longer stops
 * a tool/weapon/armor piece from working at all (including removing {@code
 * gathering.GatheringListener}'s own older Axe/Pickaxe break-speed=0 gate, explicitly superseded
 * too, per the user's own confirmation), it just costs extra durability. See {@link
 * #extraDamageMultiplier} and {@code ProficiencyDurabilityListener} for the actual mechanic, and
 * {@code client.EquipmentSkillWarningOverlay} for the advisory HUD warning (now covering armor too,
 * since nothing blocks it anymore).
 *
 * <p>Placeholder thresholds, easy to retune -- re-spaced from the original Section 6 proposal
 * (Stone 5, Iron 15, Diamond 30, Netherite 45) now that Copper sits between Stone and Iron,
 * widening 4 gated tiers to 5. Tier 0 (Wood) is always unlocked -- every player starts with it.
 * Legendary (Tier 6) has no threshold at all -- {@link #unlockLevel} returns {@code
 * Integer.MAX_VALUE} for it, since it's unreachable through any current player path anyway (see
 * {@code EquipmentTierLadder#MAX_REACHABLE_TIER}), so gating it would be meaningless.
 */
public final class ToolTierUnlocks {

    private static final int[] UNLOCK_LEVEL_BY_TIER = {
            0,  // Tier 0 - Wood
            5,  // Tier 1 - Stone
            12, // Tier 2 - Copper
            20, // Tier 3 - Iron
            30, // Tier 4 - Gold
            42  // Tier 5 - Diamond
    };

    private ToolTierUnlocks() {
    }

    public static int unlockLevel(int tier) {
        if (tier < 0 || tier >= UNLOCK_LEVEL_BY_TIER.length) {
            return Integer.MAX_VALUE;
        }
        return UNLOCK_LEVEL_BY_TIER[tier];
    }

    /**
     * The highest tier {@code level} has unlocked, as a display name (e.g. {@code "Copper"}) --
     * shared Skills-screen benefit-line helper, moved here (from a private duplicate in {@code
     * gathering.GatheringListener}) once this table started backing more than just Lumberjack/Miner.
     */
    public static String unlockedTierName(int level) {
        return EquipmentTierLadder.tierDisplayName(unlockedTier(level));
    }

    /** The highest tier {@code level} has unlocked, as a raw tier index. */
    public static int unlockedTier(int level) {
        int unlocked = 0;
        for (int tier = 1; tier <= EquipmentTierLadder.MAX_REACHABLE_TIER; tier++) {
            if (level >= unlockLevel(tier)) {
                unlocked = tier;
            }
        }
        return unlocked;
    }

    /**
     * The extra durability-damage multiplier for using/wearing {@code attemptedTier} gear while
     * only {@code unlockedTier} is actually unlocked (2026-10-06, user's own formula, corrected
     * same day): {@code 0} if proficient (at or below your unlocked tier), otherwise {@code
     * gap = attemptedTier - unlockedTier} -- 1 tier above costs double (2x total) durability
     * damage, 2 tiers above costs triple (3x total), 3 tiers above costs quadruple (4x total), and
     * so on -- total multiplier is always {@code gap + 1}, no grace band.
     */
    public static double extraDamageMultiplier(int attemptedTier, int unlockedTier) {
        int gap = attemptedTier - unlockedTier;
        return Math.max(0, gap);
    }
}
