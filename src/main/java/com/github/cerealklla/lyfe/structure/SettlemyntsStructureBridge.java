package com.github.cerealklla.lyfe.structure;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

import com.github.cerealklla.settlemynts.api.Settlemynts;
import com.github.cerealklla.settlemynts.bridge.YconomicsShopBridge;
import com.github.cerealklla.settlemynts.zone.ShopResource;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;

/**
 * Resolves a crafting/cooking structure's plot and its settlement's Shop pricing against
 * Settlemynts (design doc Section 19.9, added 2026-10-05) -- this class must only ever be
 * referenced from behind a {@code ModList.get().isLoaded("settlemynts")} check at the call site,
 * same isolation rule every other optional sibling-mod dependency in this suite uses (see
 * Settlemynts' own {@code bridge.YconomicsBillBridge}'s class doc for the precedent this follows).
 * Converts between this feature's own {@link UpgradeCostEntry} and Settlemynts' {@code
 * zone.ShopResource} at this one boundary.
 */
public final class SettlemyntsStructureBridge {

    private SettlemyntsStructureBridge() {
    }

    public static boolean isAvailable() {
        return net.neoforged.fml.ModList.get().isLoaded("settlemynts");
    }

    public record PlotInfo(UUID plotId, UUID settlementCoreId) {
    }

    /** Which plot (if any) a structure at {@code pos} sits on -- empty if there's no finalized Settlemynts plot there at all. */
    public static Optional<PlotInfo> findPlotAt(ServerLevel level, BlockPos pos) {
        return Settlemynts.findPlotAt(level, pos).map(h -> new PlotInfo(h.plotId(), h.settlementCoreId()));
    }

    /** Every real container located within {@code plotId}'s own polygon -- the "on-hand" resource pool. */
    public static List<Container> resolvePlotBoxes(ServerLevel level, UUID plotId) {
        return Settlemynts.resolvePlotBoxes(level, plotId);
    }

    public static OptionalInt getAverageSettlementPrice(ServerLevel level, UUID settlementCoreId, UpgradeCostEntry entry) {
        return Settlemynts.getAverageSettlementPrice(level, settlementCoreId, toShopResource(entry));
    }

    /** The first plot in {@code settlementCoreId}'s settlement that actually sells {@code entry} -- for performing a real purchase, not just reading a price. */
    public static Optional<UUID> findSellingPlot(ServerLevel level, UUID settlementCoreId, UpgradeCostEntry entry) {
        return Settlemynts.findSellingPlot(level, settlementCoreId, toShopResource(entry));
    }

    public record NearbySettlement(UUID settlementCoreId, double distanceBlocks) {
    }

    public static List<NearbySettlement> findNearbySettlements(ServerLevel level, BlockPos origin, double radiusBlocks) {
        return Settlemynts.findNearbySettlements(level, origin, radiusBlocks).stream()
                .map(h -> new NearbySettlement(h.settlementCoreId(), h.distanceBlocks()))
                .toList();
    }

    public record PurchaseResult(int filled, int nuggetsCharged) {
    }

    public static PurchaseResult purchase(ServerLevel level, UUID plotId, UpgradeCostEntry entry, int quantity, List<Container> paymentBoxes) {
        YconomicsShopBridge.PurchaseResult result = Settlemynts.purchaseFromSettlementShop(level, plotId, toShopResource(entry), quantity, paymentBoxes);
        return new PurchaseResult(result.filled(), result.nuggetsCharged());
    }

    public static int getNuggetBalance(Player player) {
        return YconomicsShopBridge.isAvailable() ? YconomicsShopBridge.getNuggetBalance(player) : 0;
    }

    public static boolean withdrawNuggets(Player player, int amount) {
        return YconomicsShopBridge.isAvailable() && YconomicsShopBridge.withdrawNuggets(player, amount);
    }

    private static ShopResource toShopResource(UpgradeCostEntry entry) {
        return entry.tag().isPresent() ? ShopResource.ofTag(entry.tag().get()) : ShopResource.ofItem(entry.itemId().get());
    }
}
