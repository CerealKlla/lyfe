package com.github.cerealklla.lyfe.merchant;

import java.util.Optional;

import com.github.cerealklla.lyfe.api.Lyfe;
import com.github.cerealklla.lyfe.skill.Skills;
import com.github.cerealklla.yconomics.shop.PurchaseLock;

import net.minecraft.network.chat.Component;

/**
 * Level-gated {@link PurchaseLock}s for Coin Purse tiers 6-8 (levels 25/35/45 -- design doc /
 * decisions.md, 2026-09-26). Tiers 0-5 are auto-granted by leveling ({@code MerchantListener}); T6+
 * is purchase-only, this class is just the "is this player currently allowed to buy" check.
 *
 * <p><b>Stub, by explicit user request</b>: who sells these tiers and at what price are both still
 * undecided, so nothing in the codebase actually attaches these locks to a real trade/vendor yet.
 * This exists as the reusable unlock-check ready for whenever that vendor is built -- the same
 * {@link PurchaseLock} mechanism a future "out of stock" case would use, not a one-off.
 */
public final class CoinPurseTierUnlocks {

    public static final int TIER_6_LEVEL = 25;
    public static final int TIER_7_LEVEL = 35;
    public static final int TIER_8_LEVEL = 45;

    private CoinPurseTierUnlocks() {
    }

    /** Empty (unlocked) for any tier outside 6-8, since those aren't purchase-gated at all. */
    public static PurchaseLock forTier(int tier) {
        int requiredLevel = switch (tier) {
            case 6 -> TIER_6_LEVEL;
            case 7 -> TIER_7_LEVEL;
            case 8 -> TIER_8_LEVEL;
            default -> 0;
        };
        if (requiredLevel <= 0) {
            return PurchaseLock.unlocked();
        }
        return player -> Lyfe.getLevel(player, Skills.MERCHANT_ID) >= requiredLevel
                ? Optional.empty()
                : Optional.of(Component.literal("Requires Merchant level " + requiredLevel));
    }
}
