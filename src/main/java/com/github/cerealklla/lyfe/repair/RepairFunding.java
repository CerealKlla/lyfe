package com.github.cerealklla.lyfe.repair;

import java.util.List;
import java.util.Optional;

import com.github.cerealklla.lyfe.structure.FundingOption;
import com.github.cerealklla.lyfe.structure.MarketPurchasing;
import com.github.cerealklla.lyfe.structure.SettlemyntsStructureBridge;
import com.github.cerealklla.lyfe.structure.UpgradeCostEntry;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * The three repair-funding options at the Anvil/Grindstone's new {@link RepairStructureMenu}
 * (2026-10-07 user spec) -- same 3-way shape as {@code structure.StructureUpgradeFunding} ("the same
 * 3 buttons we have for upgrading a plot... they'll behave the same way"), but genuinely different in
 * one respect: the "on-hand" pool is the *player's own carried inventory* ({@link
 * RepairInventoryTransfer}), never a plot's boxes -- repairing your own gear isn't tied to owning a
 * Settlemynts plot the way a crafting structure's tier upgrade is. Because of that, there's no
 * "free if not on a plot" graceful degradation here either: {@link FundingOption#ON_HAND} always
 * costs real materials from the player regardless of Settlemynts being loaded at all; only the
 * MIX/GOLD_ONLY purchase step (buying a shortfall from the town) needs Settlemynts and a nearby plot
 * -- if neither is available, those options simply can't cover a shortfall and fail with a clear
 * message, rather than silently granting a free repair.
 */
public final class RepairFunding {

    private RepairFunding() {
    }

    public record Result(boolean success, String message) {
        static Result ok() {
            return new Result(true, null);
        }

        static Result fail(String message) {
            return new Result(false, message);
        }
    }

    public static Result repair(ServerPlayer player, ServerLevel level, BlockPos stationPos, ItemStack stack, FundingOption option) {
        if (stack.isEmpty()) {
            return Result.fail("Put a damaged item in the slot first.");
        }
        if (RepairableItem.of(stack) == null) {
            return Result.fail("This item can't be repaired here.");
        }
        UpgradeCostEntry cost = RepairCost.costFor(stack);
        if (cost == null) {
            return Result.fail("This item is already fully repaired.");
        }
        int quantity = cost.amount();

        int onHand = option == FundingOption.GOLD_ONLY ? 0 : RepairInventoryTransfer.countAvailable(player, cost);
        int onHandUsed = Math.min(onHand, quantity);
        int shortfall = quantity - onHandUsed;

        if (option == FundingOption.ON_HAND) {
            if (shortfall > 0) {
                return Result.fail("Missing " + shortfall + " more " + cost.label() + ".");
            }
            RepairInventoryTransfer.drain(player, cost, quantity);
            stack.setDamageValue(0);
            return Result.ok();
        }

        int purchaseQuantity = option == FundingOption.GOLD_ONLY ? quantity : shortfall;
        MarketPurchasing.Purchase purchase = null;
        if (purchaseQuantity > 0) {
            if (!SettlemyntsStructureBridge.isAvailable()) {
                return Result.fail("No settlement economy available to buy " + cost.label() + " from.");
            }
            Optional<SettlemyntsStructureBridge.PlotInfo> plotInfo = SettlemyntsStructureBridge.findPlotAt(level, stationPos);
            if (plotInfo.isEmpty()) {
                return Result.fail("This station isn't in a settlement -- nowhere to buy " + cost.label() + " from.");
            }
            purchase = MarketPurchasing.resolvePurchase(level, plotInfo.get().settlementCoreId(), stationPos, cost, purchaseQuantity);
            if (purchase == null) {
                return Result.fail("Nobody sells " + cost.label() + " nearby.");
            }
        }

        int totalCharge = purchase == null ? 0 : purchase.buyerCharge();
        if (SettlemyntsStructureBridge.getNuggetBalance(player) < totalCharge) {
            return Result.fail("You need " + totalCharge + " Gold Nuggets for the remaining " + cost.label() + ".");
        }

        if (option == FundingOption.MIX && onHandUsed > 0) {
            RepairInventoryTransfer.drain(player, cost, onHandUsed);
        }
        if (purchase != null) {
            List<Container> sellerBoxes = SettlemyntsStructureBridge.resolvePlotBoxes(level, purchase.sellerPlotId());
            SettlemyntsStructureBridge.purchase(level, purchase.sellerPlotId(), purchase.entry(), purchase.quantity(), sellerBoxes);
            SettlemyntsStructureBridge.withdrawNuggets(player, totalCharge);
        }
        stack.setDamageValue(0);
        return Result.ok();
    }
}
