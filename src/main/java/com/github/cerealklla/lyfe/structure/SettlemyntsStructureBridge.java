package com.github.cerealklla.lyfe.structure;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

import com.github.cerealklla.settlemynts.api.Settlemynts;
import com.github.cerealklla.settlemynts.bridge.YconomicsShopBridge;
import com.github.cerealklla.settlemynts.zone.ShopResource;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
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

    /**
     * {@code tier} (added 2026-10-09) is the plot's own unlocked construction-Tier cap -- "do not
     * allow the structure to upgrade past the limit of the plot itself." {@code zoneTypeId} (added
     * for the Recallcinite Totem feature) is the plot's assigned Blueprint/Zone type id (e.g.
     * {@code blueprynts:recallcinite_stone}), needed to detect "is this plot zoned as a Recallcinite
     * Stone" without a second cross-mod call into Blueprynts.
     */
    public record PlotInfo(UUID plotId, UUID settlementCoreId, int tier, Identifier zoneTypeId) {
    }

    /** Which plot (if any) a structure at {@code pos} sits on -- empty if there's no finalized Settlemynts plot there at all. */
    public static Optional<PlotInfo> findPlotAt(ServerLevel level, BlockPos pos) {
        return Settlemynts.findPlotAt(level, pos).map(h -> new PlotInfo(h.plotId(), h.settlementCoreId(), h.tier(), h.zoneTypeId()));
    }

    /**
     * Added 2026-10-09, real report: "when a player is interacting with a crafting/cooking structure
     * in a plot and they are not the owner of the plot, do not show the upgrade button." Thin wrapper
     * over {@code api.Settlemynts#canManagePlotAt} -- {@code false} if there's no plot at {@code pos}
     * at all (the caller treats "no plot" as its own separate, already-blocking case).
     */
    public static boolean canManagePlotAt(ServerLevel level, BlockPos pos, java.util.UUID playerId) {
        return Settlemynts.canManagePlotAt(level, pos, playerId);
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
