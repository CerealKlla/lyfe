package com.github.cerealklla.lyfe.gathering;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * A player-agnostic world mechanic, NOT part of the Farmer skill (design doc Section 23,
 * 2026-10-04 user request) -- same category as {@link LeafDecayAccelerator}, an unrelated QoL
 * mechanic sharing this package with a real skill. Any sapling item that comes to rest on the
 * ground (most commonly from decayed leaves after a tree falls, chopped/burned/exploded) has a
 * flat 10% chance of instantly planting itself instead of sitting as a dropped item, purely to
 * give forests a chance to auto-regrow. Deliberately excludes every seed that requires tilled
 * farmland (Wheat/Carrot/Potato/Beetroot/Melon/Pumpkin) -- those aren't {@link SaplingBlock}s, so
 * they're naturally out of scope without any extra check.
 *
 * <p>Each item entity is rolled exactly once, the instant it first settles on the ground -- a
 * persistent-data marker tag prevents re-rolling every tick while it continues to sit there.
 */
public final class SaplingAutoReplant {

    private static final double REPLANT_CHANCE = 0.10;
    private static final String ROLLED_TAG = "lyfe_sapling_replant_rolled";

    @SubscribeEvent
    public void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof ItemEntity itemEntity)) {
            return;
        }
        if (!(itemEntity.level() instanceof ServerLevel serverLevel) || !itemEntity.onGround()) {
            return;
        }
        if (itemEntity.getPersistentData().getBooleanOr(ROLLED_TAG, false)) {
            return;
        }
        itemEntity.getPersistentData().putBoolean(ROLLED_TAG, true);

        ItemStack stack = itemEntity.getItem();
        if (!(stack.getItem() instanceof BlockItem blockItem) || !(blockItem.getBlock() instanceof SaplingBlock saplingBlock)) {
            return;
        }
        if (serverLevel.getRandom().nextDouble() >= REPLANT_CHANCE) {
            return;
        }

        BlockPos pos = BlockPos.containing(itemEntity.getX(), itemEntity.getY(), itemEntity.getZ());
        if (!serverLevel.isEmptyBlock(pos)) {
            return;
        }
        BlockState state = saplingBlock.defaultBlockState();
        if (!state.canSurvive(serverLevel, pos)) {
            return;
        }

        serverLevel.setBlockAndUpdate(pos, state);
        stack.shrink(1);
        if (stack.isEmpty()) {
            itemEntity.discard();
        }
    }
}
