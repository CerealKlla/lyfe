package com.github.cerealklla.lyfe.farming;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.github.cerealklla.lyfe.api.Lyfe;
import com.github.cerealklla.lyfe.skill.Skills;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.BonemealEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

/**
 * Grants Farmer XP and applies its four level-scaled bonuses (design doc Section 23, 2026-10-04
 * user request): BonusSeedChance, RareLeafDropBonus, CropYieldDoubleChance, BonemealSaveChance.
 * The standalone sapling-auto-replant mechanic is deliberately NOT here -- it's not part of this
 * skill at all, see {@code gathering.SaplingAutoReplant}.
 *
 * <p>All magnitudes below are the user's own given numbers, not placeholders -- see decisions.md,
 * 2026-10-04, including the same-day correction that BonusSeedChance has no 30% cap.
 */
public final class FarmerListener {

    private static final long XP_PER_HARVEST = 5;

    // 1%/level, uncapped except by the shared MAX_LEVEL (50) itself -- 50% chance at level 50.
    private static final double SEED_CHANCE_PER_LEVEL = 0.01;

    // +2% per 10 levels, caps at +10% at level 50. Boosts vanilla's own existing sapling/apple roll
    // directly (additive), only when breaking leaves with a Hoe specifically.
    private static final double LEAF_BONUS_PER_10_LEVELS = 0.02;
    private static final double LEAF_BONUS_CAP = 0.10;

    // 30% at level 20, +0.5%/level after, capping at level 50 (30 + 0.5*30 = 45%).
    private static final int YIELD_DOUBLE_UNLOCK_LEVEL = 20;
    private static final double YIELD_DOUBLE_BASE = 0.30;
    private static final double YIELD_DOUBLE_PER_LEVEL_AFTER = 0.005;
    private static final double YIELD_DOUBLE_CAP = 0.45;

    // 5% per 5 levels, caps at 50% at level 50.
    private static final double BONEMEAL_SAVE_PER_5_LEVELS = 0.05;
    private static final double BONEMEAL_SAVE_CAP = 0.50;

    private static final Map<Block, Item> CROP_BONUS_SEED = Map.of(
            Blocks.WHEAT, Items.WHEAT_SEEDS,
            Blocks.CARROTS, Items.CARROT,
            Blocks.POTATOES, Items.POTATO,
            Blocks.BEETROOTS, Items.BEETROOT_SEEDS
    );

    private static final Map<Block, Item> LEAVES_TO_SAPLING = Map.ofEntries(
            Map.entry(Blocks.OAK_LEAVES, Items.OAK_SAPLING),
            Map.entry(Blocks.SPRUCE_LEAVES, Items.SPRUCE_SAPLING),
            Map.entry(Blocks.BIRCH_LEAVES, Items.BIRCH_SAPLING),
            Map.entry(Blocks.JUNGLE_LEAVES, Items.JUNGLE_SAPLING),
            Map.entry(Blocks.ACACIA_LEAVES, Items.ACACIA_SAPLING),
            Map.entry(Blocks.DARK_OAK_LEAVES, Items.DARK_OAK_SAPLING),
            Map.entry(Blocks.CHERRY_LEAVES, Items.CHERRY_SAPLING)
    );

    // Matches vanilla's real apple-drop eligibility -- only these two leaf types ever drop an apple.
    private static final Set<Block> APPLE_ELIGIBLE_LEAVES = Set.of(Blocks.OAK_LEAVES, Blocks.DARK_OAK_LEAVES);

    @SubscribeEvent
    public void onBlockDrops(BlockDropsEvent event) {
        BlockState state = event.getState();
        Entity breaker = event.getBreaker();
        if (!(breaker instanceof Player player) || !(state.getBlock() instanceof CropBlock cropBlock)) {
            onLeafBlockDrops(event);
            return;
        }
        if (!cropBlock.isMaxAge(state)) {
            return; // Only a fully-grown crop counts as a real harvest.
        }

        ServerLevel serverLevel = event.getLevel();
        Lyfe.addXp(player, Skills.FARMER_ID, XP_PER_HARVEST);
        int level = Lyfe.getLevel(player, Skills.FARMER_ID);

        rollBonusSeed(event, serverLevel, state, level);
        rollYieldDouble(event, serverLevel, level);
    }

    /**
     * Separate check for leaf-breaking, since it's a wholly different block type/condition (needs a
     * Hoe) from crop harvesting -- routed through from {@link #onBlockDrops} rather than its own
     * {@code @SubscribeEvent} method so both checks share one event dispatch.
     */
    private void onLeafBlockDrops(BlockDropsEvent event) {
        BlockState state = event.getState();
        Entity breaker = event.getBreaker();
        if (!(breaker instanceof Player player) || !(state.getBlock() instanceof LeavesBlock)) {
            return;
        }
        if (!(player.getMainHandItem().getItem() instanceof HoeItem)) {
            return;
        }

        int level = Lyfe.getLevel(player, Skills.FARMER_ID);
        double bonus = Math.min(LEAF_BONUS_CAP, Math.floorDiv(level, 10) * LEAF_BONUS_PER_10_LEVELS);
        if (bonus <= 0) {
            return;
        }

        ServerLevel serverLevel = event.getLevel();
        Block leafBlock = state.getBlock();
        Item sapling = LEAVES_TO_SAPLING.get(leafBlock);
        if (sapling != null && !containsItem(event, sapling) && serverLevel.getRandom().nextDouble() < bonus) {
            addDrop(event, serverLevel, sapling);
        }
        if (APPLE_ELIGIBLE_LEAVES.contains(leafBlock) && !containsItem(event, Items.APPLE)
                && serverLevel.getRandom().nextDouble() < bonus) {
            addDrop(event, serverLevel, Items.APPLE);
        }
    }

    @SubscribeEvent
    public void onBonemeal(BonemealEvent event) {
        if (!event.isValidBonemealTarget()) {
            return;
        }
        Player player = event.getPlayer();
        if (player == null || !(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }

        int level = Lyfe.getLevel(player, Skills.FARMER_ID);
        double chance = Math.min(BONEMEAL_SAVE_CAP, Math.floorDiv(level, 5) * BONEMEAL_SAVE_PER_5_LEVELS);
        if (chance <= 0 || serverLevel.getRandom().nextDouble() >= chance) {
            return;
        }

        // Perform the same effect BoneMealItem#applyBonemeal would, then cancel so its own
        // itemStack.shrink(1) (which runs after this event, only when not cancelled) never happens --
        // the bone meal is spent on a real plant-growth effect either way, just not consumed.
        BlockState state = event.getState();
        if (state.getBlock() instanceof BonemealableBlock bonemealableBlock
                && bonemealableBlock.isBonemealSuccess(serverLevel, serverLevel.getRandom(), event.getPos(), state)) {
            bonemealableBlock.performBonemeal(serverLevel, serverLevel.getRandom(), event.getPos(), state);
        }
        event.setSuccessful(true);
        event.setCanceled(true);
    }

    private void rollBonusSeed(BlockDropsEvent event, ServerLevel serverLevel, BlockState state, int level) {
        double chance = level * SEED_CHANCE_PER_LEVEL;
        if (serverLevel.getRandom().nextDouble() >= chance) {
            return;
        }
        Item seed = CROP_BONUS_SEED.get(state.getBlock());
        if (seed != null) {
            addDrop(event, serverLevel, seed);
        }
    }

    private void rollYieldDouble(BlockDropsEvent event, ServerLevel serverLevel, int level) {
        if (level < YIELD_DOUBLE_UNLOCK_LEVEL) {
            return;
        }
        double chance = yieldDoublePercent(level) / 100.0;
        if (serverLevel.getRandom().nextDouble() >= chance) {
            return;
        }
        for (ItemEntity drop : event.getDrops()) {
            drop.getItem().grow(drop.getItem().getCount());
        }
    }

    private static boolean containsItem(BlockDropsEvent event, Item item) {
        for (ItemEntity drop : event.getDrops()) {
            if (drop.getItem().is(item)) {
                return true;
            }
        }
        return false;
    }

    private static void addDrop(BlockDropsEvent event, ServerLevel serverLevel, Item item) {
        List<ItemEntity> drops = event.getDrops();
        double x;
        double y;
        double z;
        if (!drops.isEmpty()) {
            ItemEntity source = drops.get(0);
            x = source.getX();
            y = source.getY();
            z = source.getZ();
        } else {
            BlockPos pos = event.getPos();
            x = pos.getX() + 0.5;
            y = pos.getY() + 0.5;
            z = pos.getZ() + 0.5;
        }
        drops.add(new ItemEntity(serverLevel, x, y, z, new ItemStack(item)));
    }

    private static double yieldDoublePercent(int level) {
        if (level < YIELD_DOUBLE_UNLOCK_LEVEL) {
            return 0;
        }
        return Math.min(YIELD_DOUBLE_CAP, YIELD_DOUBLE_BASE + (level - YIELD_DOUBLE_UNLOCK_LEVEL) * YIELD_DOUBLE_PER_LEVEL_AFTER) * 100;
    }

    /**
     * Live benefit readout for the Skills screen (common.skill.SkillBenefits) -- current computed
     * values only, no formulas, matching every other skill's own benefit-line convention.
     */
    public static List<String> benefitLines(int level) {
        double seedPercent = level * SEED_CHANCE_PER_LEVEL * 100;
        double leafPercent = Math.min(LEAF_BONUS_CAP, Math.floorDiv(level, 10) * LEAF_BONUS_PER_10_LEVELS) * 100;
        double yieldPercent = yieldDoublePercent(level);
        double bonemealPercent = Math.min(BONEMEAL_SAVE_CAP, Math.floorDiv(level, 5) * BONEMEAL_SAVE_PER_5_LEVELS) * 100;
        return List.of(
                "Bonus seed chance " + formatPercent(seedPercent) + "%",
                "Rare leaf drop bonus (with a Hoe) +" + formatPercent(leafPercent) + "%",
                "Crop yield doubling chance " + formatPercent(yieldPercent) + "%",
                "Bonemeal not consumed chance " + formatPercent(bonemealPercent) + "%",
                // 2026-10-06: Farmer also governs Hoe's tier-unlock gate (EquipmentTierLadder#governingSkillId).
                "Unlocked Hoe tier: " + com.github.cerealklla.lyfe.craft.ToolTierUnlocks.unlockedTierName(level)
        );
    }

    private static String formatPercent(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
