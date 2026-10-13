package com.github.cerealklla.lyfe.repair;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import com.github.cerealklla.lyfe.api.Lyfe;
import com.github.cerealklla.lyfe.craft.CrafterConstants;
import com.github.cerealklla.lyfe.skill.Skills;
import com.github.cerealklla.lyfe.structure.FundingOption;
import com.github.cerealklla.lyfe.structure.MarketPurchasing;
import com.github.cerealklla.lyfe.structure.SettlemyntsStructureBridge;
import com.github.cerealklla.lyfe.structure.UpgradeCostEntry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
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

    // [RepairDebug] -- temporary, chasing a real live report (2026-10-13): a single click of
    // "Repair with Resources" on Golden Boots reportedly drained far more than this method's own
    // hard-capped-at-9 formula should ever allow in one call, and still failed with "insufficient."
    // Logs every real invocation of #repair with its full inputs/outputs so a reproduction reveals
    // whether this is being called more than once per click, and with what numbers each time.
    // Remove once the real root cause is found and fixed.
    private static final Logger LOGGER = LogUtils.getLogger();

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
        LOGGER.info("[RepairDebug] repair() called: player={} option={} item={} damage={}/{}",
                player.getName().getString(), option, BuiltInRegistries.ITEM.getKey(stack.getItem()),
                stack.getDamageValue(), stack.getMaxDamage());
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
        LOGGER.info("[RepairDebug] quantity={} onHand={} onHandUsed={} shortfall={}", quantity, onHand, onHandUsed, shortfall);

        if (option == FundingOption.ON_HAND) {
            if (shortfall > 0) {
                LOGGER.info("[RepairDebug] ON_HAND failing with shortfall {} -- no drain should happen", shortfall);
                return Result.fail("Missing " + shortfall + " more " + cost.label() + ".");
            }
            LOGGER.info("[RepairDebug] ON_HAND succeeding, draining {}", quantity);
            RepairInventoryTransfer.drain(player, cost, quantity);
            grantRepairXp(player, stack);
            stack.setDamageValue(0);
            return Result.ok();
        }

        int purchaseQuantity = option == FundingOption.GOLD_ONLY ? quantity : shortfall;
        LOGGER.info("[RepairDebug] {} path, purchaseQuantity={}", option, purchaseQuantity);
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
        grantRepairXp(player, stack);
        stack.setDamageValue(0);
        return Result.ok();
    }

    /**
     * "Repairing something should reward you with Crafter XP, up to 0-60% (based on how damaged it
     * was) of whatever crafting the base item would grant" -- explicit user spec, 2026-10-10. Must
     * be called *before* {@code stack.setDamageValue(0)} (both call sites above) -- reads the still-
     * damaged stack to get how damaged it actually was. Crafter XP-per-craft is a flat amount
     * regardless of item/tier ({@link CrafterConstants#CRAFTER_XP_PER_CRAFT}, see {@code
     * craft.CraftingStructureMenu}'s own use of it), so "what crafting this would grant" is always
     * that same baseline for anything actually on the crafting ladder. An off-ladder item ({@link
     * RepairableItem.TierInfo#GENERIC} -- a shield, trident, elytra, fishing rod) was never
     * craftable through this system at all, so there's no crafting-XP baseline to scale a reward
     * off -- grants nothing for those, not a guessed flat amount.
     */
    private static void grantRepairXp(ServerPlayer player, ItemStack stack) {
        RepairableItem.TierInfo tierInfo = RepairableItem.of(stack);
        if (tierInfo == null || tierInfo.tier() < 0) {
            return;
        }
        double missingFraction = stack.getDamageValue() / (double) stack.getMaxDamage();
        long reward = Math.round(CrafterConstants.CRAFTER_XP_PER_CRAFT * missingFraction * 0.60);
        if (reward > 0) {
            Lyfe.addXp(player, Skills.CRAFTER_ID, reward);
        }
    }
}
