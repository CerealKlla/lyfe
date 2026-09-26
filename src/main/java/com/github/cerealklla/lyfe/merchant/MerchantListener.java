package com.github.cerealklla.lyfe.merchant;

import java.util.Optional;

import com.github.cerealklla.lyfe.api.Lyfe;
import com.github.cerealklla.lyfe.skill.Skills;
import com.github.cerealklla.yconomics.api.Yconomics;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent;

/**
 * The Merchant skill (design doc / decisions.md, 2026-09-26): 1:1 XP from completed NPC trades
 * (buying and selling, never player shops -- {@link TradeWithVillagerEvent} only ever fires for a
 * real {@code AbstractVillager}, so that exclusion is automatic, not something this class checks),
 * a buy/sell price bonus scaling up to {@link #MAX_PRICE_BONUS} at {@link Skills#MAX_LEVEL}, and
 * (via Yconomics) auto-raising the player's Coin Purse tier every 2 levels up to T5 -- T6-T8 are
 * purchase-only (see {@link CoinPurseTierUnlocks}), not auto-granted here.
 *
 * <p>Registered only when Yconomics is loaded (see {@code LyfeMod}) -- the whole point of this
 * skill is Coin Purse integration, so unlike Cartographyr's soft-dependency skills there's no
 * meaningful "standalone" mode to preserve.
 *
 * <p><b>Buy-side discount</b> uses {@link MerchantOffer#setSpecialPriceDiff(int)} -- vanilla's own
 * Hero-of-the-Village discount mechanism -- applied fresh on {@link PlayerContainerEvent.Open} and
 * reset on {@link PlayerContainerEvent.Close}. Resetting on close matters: a {@code MerchantOffer}
 * is a shared object living on the villager entity itself, not per-player, so leaving a discount
 * applied after this player closes the screen would leak it to the next (unskilled) player to trade
 * with the same villager. Only the cost-A slot is discountable this way -- vanilla's own
 * {@code MerchantOffer#getModifiedCostCount} never applies {@code specialPriceDiff} to cost-B, so a
 * trade priced entirely on cost-B doesn't get a buy discount (a real, documented limitation, not an
 * oversight, confirmed by reading the decompiled source).
 *
 * <p><b>Sell-side bonus</b> has no equivalent field to adjust (a {@code MerchantOffer}'s result is
 * a fixed {@code ItemStack}, with no multiplier), so it's paid out as extra Gold Nuggets directly
 * into the player's inventory in the {@link TradeWithVillagerEvent} handler, right after the trade
 * completes -- {@code currency.CoinPurseListener}'s own container-close sweep folds it into the
 * purse a moment later, same as any other loose nugget.
 */
public final class MerchantListener {

    public static final double MAX_PRICE_BONUS = 0.20;

    // Every 2 Merchant levels raises Coin Purse tier by one, capping at T5 (level 10) -- the
    // original spec's auto-leveling half of the design; T6+ requires purchase, see
    // CoinPurseTierUnlocks. increaseCoinPurseTierTo never lowers, so recomputing this every trade
    // is a safe no-op once the cap is reached.
    private static final int AUTO_TIER_CAP = 5;

    @SubscribeEvent
    public void onTrade(TradeWithVillagerEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MerchantOffer offer = event.getMerchantOffer();
        int baseline = baselineNuggetValue(offer);
        if (baseline <= 0) {
            return;
        }

        Lyfe.addXp(player, Skills.MERCHANT_ID, baseline);
        int level = Lyfe.getLevel(player, Skills.MERCHANT_ID);
        Yconomics.increaseCoinPurseTierTo(player, Math.min(AUTO_TIER_CAP, level / 2));

        if (offer.getResult().is(Items.GOLD_NUGGET)) {
            int bonus = (int) Math.round(baseline * bonusFraction(level));
            if (bonus > 0) {
                ItemStack reward = new ItemStack(Items.GOLD_NUGGET, bonus);
                if (!player.getInventory().add(reward)) {
                    player.drop(reward, false);
                }
            }
        }
    }

    @SubscribeEvent
    public void onContainerOpen(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getContainer() instanceof MerchantMenu merchantMenu)) {
            return;
        }
        double fraction = bonusFraction(Lyfe.getLevel(player, Skills.MERCHANT_ID));
        if (fraction <= 0) {
            return;
        }
        for (MerchantOffer offer : merchantMenu.getOffers()) {
            if (offer.getItemCostA().itemStack().is(Items.GOLD_NUGGET)) {
                int discount = (int) Math.round(offer.getItemCostA().count() * fraction);
                offer.setSpecialPriceDiff(-discount);
            }
        }
    }

    @SubscribeEvent
    public void onContainerClose(PlayerContainerEvent.Close event) {
        if (event.getContainer() instanceof MerchantMenu merchantMenu) {
            for (MerchantOffer offer : merchantMenu.getOffers()) {
                offer.resetSpecialPriceDiff();
            }
        }
    }

    private static double bonusFraction(int level) {
        return Math.min(MAX_PRICE_BONUS, (double) level / Skills.MAX_LEVEL * MAX_PRICE_BONUS);
    }

    /**
     * The trade's baseline Gold Nugget value, ignoring any current discount -- {@link
     * ItemCost#count()} is the raw data-driven number, unaffected by {@link
     * MerchantOffer#getSpecialPriceDiff()}/demand, so XP doesn't shrink as the buy discount grows.
     * Positive for a buying trade (cost includes nuggets); for a selling trade (result is nuggets
     * instead), falls back to the result's fixed count.
     */
    private static int baselineNuggetValue(MerchantOffer offer) {
        int cost = nuggetAmount(offer.getItemCostA());
        Optional<ItemCost> costB = offer.getItemCostB();
        if (costB.isPresent()) {
            cost += nuggetAmount(costB.get());
        }
        if (cost > 0) {
            return cost;
        }
        return offer.getResult().is(Items.GOLD_NUGGET) ? offer.getResult().getCount() : 0;
    }

    private static int nuggetAmount(ItemCost cost) {
        return cost.itemStack().is(Items.GOLD_NUGGET) ? cost.count() : 0;
    }
}
