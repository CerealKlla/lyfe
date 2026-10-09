package com.github.cerealklla.lyfe.structure;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;

/**
 * The resource-cost funding logic for a crafting/cooking structure's tier upgrade (design doc
 * Section 19.9, explicit user spec, 2026-10-05) -- shared by {@code craft.CraftingStructureMenu}
 * and {@code cook.CookingStructureMenu} so the three-option funding rules live in exactly one
 * place.
 *
 * <p><b>Graceful degradation</b>: if Settlemynts isn't loaded at all, {@link #fund} succeeds
 * immediately with no cost -- there's no plot system to gate against in that case. <b>Narrowed
 * 2026-10-09</b> -- a structure that IS on a Settlemynts server but sitting outside any finalized
 * plot used to get this same free pass, which was a real bug ("Upgrading stations is still free
 * despite it giving a cost"): per explicit spec, a station is allowed to exist outside a plot, but
 * can never be upgraded past Tier 1 there at all -- {@link #canUpgrade} is the real gate for this
 * (used by the menu to hide the button entirely), and {@link #fund} now fails outright, rather than
 * silently granting a free upgrade, if it's ever reached without a qualifying plot regardless.
 *
 * <p><b>Pricing, per the user's exact spec</b>:
 * <ul>
 *   <li>On-hand (Option 1): must fully cover every cost entry from boxes on the structure's own
 *   plot, or the whole upgrade fails with no mutation at all -- no partial consumption.</li>
 *   <li>Mix/Gold-only (Options 2/3): whatever isn't covered on-hand (all of it, for Gold-only) is
 *   bought at 3x the home settlement's own average listed sale price, or -- if nobody in the home
 *   settlement sells it at all -- from the nearest settlement that does, at 4x their price plus a
 *   flat transport fee (100 nuggets per 50-item bundle, rouned up for a partial bundle).</li>
 * </ul>
 *
 * <p><b>Known simplification, flagged not fixed</b>: the actual real Shop a purchase drains stock
 * from (found via {@code SettlemyntsStructureBridge#findSellingPlot}) is charged its own real
 * listed price by Yconomics' purchase transaction -- the buyer is additionally charged whatever
 * makes up the difference between that and the full "3x/4x+transport" total, which is not credited
 * to the seller (a market-markup sink, not a seller windfall or a buyer shortchange). This keeps
 * the seller's own economy consistent (they're paid their own asking price for their own goods)
 * while still honoring the user's literal "pay 3x/4x average" spec for the buyer's total cost.
 */
public final class StructureUpgradeFunding {

    private StructureUpgradeFunding() {
    }

    public record Result(boolean success, String message) {
        static Result ok() {
            return new Result(true, null);
        }

        static Result fail(String message) {
            return new Result(false, message);
        }
    }

    private record Need(UpgradeCostEntry entry, int onHand, int shortfall) {
    }

    /**
     * Should the Upgrade button(s) even be shown -- added 2026-10-09, three real requests at once:
     * a station outside any plot can never upgrade past Tier 1; a non-owner/non-manager of the plot
     * shouldn't see the button at all; and the structure can never upgrade past the plot's own
     * unlocked Tier cap ({@code PlotRecord#tier}, mirrored here via {@code
     * SettlemyntsStructureBridge.PlotInfo#tier}). Pure read, no mutation -- safe to call every time
     * the menu is opened/synced.
     */
    public static boolean canUpgrade(ServerPlayer player, ServerLevel level, BlockPos structurePos, int currentTier) {
        if (!SettlemyntsStructureBridge.isAvailable()) {
            return true;
        }
        Optional<SettlemyntsStructureBridge.PlotInfo> plotInfo = SettlemyntsStructureBridge.findPlotAt(level, structurePos);
        if (plotInfo.isEmpty()) {
            return false;
        }
        if (!SettlemyntsStructureBridge.canManagePlotAt(level, structurePos, player.getUUID())) {
            return false;
        }
        return currentTier + 1 <= plotInfo.get().tier();
    }

    public static Result fund(ServerPlayer player, ServerLevel level, BlockPos structurePos, int nextTier, FundingOption option) {
        if (!SettlemyntsStructureBridge.isAvailable()) {
            return Result.ok();
        }
        Optional<SettlemyntsStructureBridge.PlotInfo> plotInfo = SettlemyntsStructureBridge.findPlotAt(level, structurePos);
        if (plotInfo.isEmpty()) {
            return Result.fail("This structure must be inside a settlement plot to upgrade past Tier 1.");
        }
        if (!SettlemyntsStructureBridge.canManagePlotAt(level, structurePos, player.getUUID())) {
            return Result.fail("You don't have permission to upgrade this plot's structures.");
        }
        if (nextTier > plotInfo.get().tier()) {
            return Result.fail("This plot hasn't been upgraded to Tier " + nextTier + " yet -- upgrade the plot itself first.");
        }
        UUID plotId = plotInfo.get().plotId();
        UUID settlementCoreId = plotInfo.get().settlementCoreId();

        List<UpgradeCostEntry> cost = StructureUpgradeCost.costFor(nextTier);
        List<Container> plotBoxes = option == FundingOption.GOLD_ONLY ? List.of() : SettlemyntsStructureBridge.resolvePlotBoxes(level, plotId);

        List<Need> needs = new ArrayList<>();
        for (UpgradeCostEntry entry : cost) {
            int available = plotBoxes.isEmpty() ? 0 : StructureCostTransfer.countAvailable(plotBoxes, entry);
            int onHand = Math.min(available, entry.amount());
            needs.add(new Need(entry, onHand, entry.amount() - onHand));
        }

        if (option == FundingOption.ON_HAND) {
            for (Need need : needs) {
                if (need.shortfall() > 0) {
                    return Result.fail("Missing " + need.shortfall() + " more " + need.entry().label() + " on this plot.");
                }
            }
            for (Need need : needs) {
                StructureCostTransfer.drain(plotBoxes, need.entry(), need.entry().amount());
            }
            return Result.ok();
        }

        List<MarketPurchasing.Purchase> purchases = new ArrayList<>();
        int totalCharge = 0;
        for (Need need : needs) {
            int quantity = option == FundingOption.GOLD_ONLY ? need.entry().amount() : need.shortfall();
            if (quantity <= 0) {
                continue;
            }
            MarketPurchasing.Purchase purchase = MarketPurchasing.resolvePurchase(level, settlementCoreId, structurePos, need.entry(), quantity);
            if (purchase == null) {
                return Result.fail("Nobody sells " + need.entry().label() + " nearby.");
            }
            purchases.add(purchase);
            totalCharge += purchase.buyerCharge();
        }

        if (SettlemyntsStructureBridge.getNuggetBalance(player) < totalCharge) {
            return Result.fail("You need " + totalCharge + " Gold Nuggets for the remaining resources.");
        }

        if (option == FundingOption.MIX) {
            for (Need need : needs) {
                if (need.onHand() > 0) {
                    StructureCostTransfer.drain(plotBoxes, need.entry(), need.onHand());
                }
            }
        }
        for (MarketPurchasing.Purchase purchase : purchases) {
            List<Container> sellerBoxes = SettlemyntsStructureBridge.resolvePlotBoxes(level, purchase.sellerPlotId());
            SettlemyntsStructureBridge.purchase(level, purchase.sellerPlotId(), purchase.entry(), purchase.quantity(), sellerBoxes);
        }
        SettlemyntsStructureBridge.withdrawNuggets(player, totalCharge);
        return Result.ok();
    }
}
