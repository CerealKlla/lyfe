package com.github.cerealklla.lyfe.structure;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * The "buy a shortfall of some resource from the nearby settlement economy" logic shared by {@link
 * StructureUpgradeFunding} and {@code repair.RepairFunding} (added 2026-10-07 when repair needed the
 * exact same pricing rules) -- extracted out of {@code StructureUpgradeFunding} rather than
 * duplicated, since the two features' only real difference is *where the on-hand portion comes
 * from* (a plot's own boxes vs. the player's own carried inventory), not how a purchase is priced.
 *
 * <p>Pricing (unchanged from the original spec): the home settlement's own average listed price at
 * {@link #LOCAL_MARKUP}x, or -- if nobody in the home settlement sells it at all -- the nearest
 * settlement that does, at {@link #NEIGHBOR_MARKUP}x their price plus a flat transport fee per
 * {@link #TRANSPORT_BUNDLE_SIZE}-item bundle (rounded up for a partial bundle).
 */
public final class MarketPurchasing {

    private static final double NEIGHBOR_SEARCH_RADIUS_BLOCKS = 5000;
    private static final int TRANSPORT_FEE_PER_BUNDLE = 100;
    private static final int TRANSPORT_BUNDLE_SIZE = 50;
    private static final int LOCAL_MARKUP = 3;
    private static final int NEIGHBOR_MARKUP = 4;

    private MarketPurchasing() {
    }

    public record Purchase(UpgradeCostEntry entry, int quantity, UUID sellerPlotId, int buyerCharge) {
    }

    /** {@code null} if nobody in the home settlement or any nearby settlement sells {@code entry} at all. */
    public static Purchase resolvePurchase(ServerLevel level, UUID homeSettlementCoreId, BlockPos originPos, UpgradeCostEntry entry, int quantity) {
        OptionalInt localPrice = SettlemyntsStructureBridge.getAverageSettlementPrice(level, homeSettlementCoreId, entry);
        if (localPrice.isPresent()) {
            Optional<UUID> sellerPlot = SettlemyntsStructureBridge.findSellingPlot(level, homeSettlementCoreId, entry);
            if (sellerPlot.isPresent()) {
                return new Purchase(entry, quantity, sellerPlot.get(), quantity * localPrice.getAsInt() * LOCAL_MARKUP);
            }
        }
        for (SettlemyntsStructureBridge.NearbySettlement neighbor : SettlemyntsStructureBridge.findNearbySettlements(level, originPos, NEIGHBOR_SEARCH_RADIUS_BLOCKS)) {
            if (neighbor.settlementCoreId().equals(homeSettlementCoreId)) {
                continue;
            }
            OptionalInt neighborPrice = SettlemyntsStructureBridge.getAverageSettlementPrice(level, neighbor.settlementCoreId(), entry);
            if (neighborPrice.isEmpty()) {
                continue;
            }
            Optional<UUID> sellerPlot = SettlemyntsStructureBridge.findSellingPlot(level, neighbor.settlementCoreId(), entry);
            if (sellerPlot.isEmpty()) {
                continue;
            }
            int bundles = (int) Math.ceil(quantity / (double) TRANSPORT_BUNDLE_SIZE);
            int transportFee = bundles * TRANSPORT_FEE_PER_BUNDLE;
            int charge = quantity * neighborPrice.getAsInt() * NEIGHBOR_MARKUP + transportFee;
            return new Purchase(entry, quantity, sellerPlot.get(), charge);
        }
        return null;
    }
}
