package com.github.cerealklla.lyfe.fishing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

/**
 * Covers only the pure, no-ItemStack-construction-needed parts of the Fisherman skill -- same
 * limitation documented in Yconomics' {@code CoinPurseContentsTest} notes: constructing a real
 * {@code ItemStack} throws "Components not bound yet" in this project's plain JUnit setup (no test
 * anywhere in this repo ever constructs one), so {@code CatchBagContents}/{@code
 * SunkenTreasureItem}/{@code LockedChestItem} aren't unit-testable here -- verified by inspection
 * instead, same as Yconomics' own Coin Purse.
 */
class FishermanListenerTest {

    @Test
    void bonusLootChanceIsZeroAtLevelOneAndCapAtMaxLevel() {
        assertEquals(0.0, FishermanListener.bonusLootChance(0), 1e-9);
        assertEquals(FishingConstants.MAX_BONUS_LOOT_CHANCE, FishermanListener.bonusLootChance(50), 1e-9);
    }

    @Test
    void sunkenTreasureChanceStaysAtBaseBelowMinLevel() {
        assertEquals(FishingConstants.SUNKEN_TREASURE_BASE_CHANCE, FishermanListener.sunkenTreasureChance(1), 1e-9);
        assertEquals(FishingConstants.SUNKEN_TREASURE_BASE_CHANCE, FishermanListener.sunkenTreasureChance(29), 1e-9);
    }

    @Test
    void sunkenTreasureChanceStepsEveryFiveLevelsPastThirty() {
        assertEquals(FishingConstants.SUNKEN_TREASURE_BASE_CHANCE, FishermanListener.sunkenTreasureChance(30), 1e-9);
        assertEquals(FishingConstants.SUNKEN_TREASURE_BASE_CHANCE + FishingConstants.SUNKEN_TREASURE_STEP_BONUS,
                FishermanListener.sunkenTreasureChance(35), 1e-9);
        assertEquals(FishingConstants.SUNKEN_TREASURE_BASE_CHANCE + 4 * FishingConstants.SUNKEN_TREASURE_STEP_BONUS,
                FishermanListener.sunkenTreasureChance(50), 1e-9);
    }

    @Test
    void rollWeightStaysWithinRangeAndRoundsToTwoDecimals() {
        FishingConstants.WeightRange range = new FishingConstants.WeightRange(2.0, 15.0);
        RandomSource random = RandomSource.create(42L);
        for (int i = 0; i < 100; i++) {
            double pounds = FishermanListener.rollWeight(range, false, random);
            assertTrue(pounds >= 2.0 && pounds <= 15.0, "pounds=" + pounds);
            double scaled = pounds * 100.0;
            assertEquals(Math.round(scaled), scaled, 1e-6);
        }
    }

    @Test
    void rollWeightLuckyStaysInUpperBand() {
        FishingConstants.WeightRange range = new FishingConstants.WeightRange(2.0, 15.0);
        RandomSource random = RandomSource.create(7L);
        double lowerBound = 15.0 - (15.0 - 2.0) * FishingConstants.LUCKY_SPOT_UPPER_BAND;
        for (int i = 0; i < 100; i++) {
            double pounds = FishermanListener.rollWeight(range, true, random);
            assertTrue(pounds >= lowerBound - 0.01 && pounds <= 15.0, "pounds=" + pounds);
        }
    }
}
