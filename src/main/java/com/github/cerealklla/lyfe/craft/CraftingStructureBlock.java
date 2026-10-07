package com.github.cerealklla.lyfe.craft;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

/**
 * One tier (2-5) of the crafting-structure ladder (design doc Section 19.6): Crafting Mat, Tinker
 * Bench, Crafting Table, Engineer's Bench. Tier 1 ("Player Inventory") needs no placed block at
 * all -- it's reachable directly from the player's own inventory, same way vanilla's personal 2x2
 * grid always is (not built this slice -- no inventory-screen button yet, flagged as a follow-up
 * polish item, not an architectural gap).
 *
 * <p>One shared Block/BlockEntity/Menu/Screen implementation, parameterized by {@code tier}, same
 * shape every other tiered thing in this mod uses (e.g. Yconomics' Coin Purse tiers) rather than 4
 * near-duplicate classes.
 */
public class CraftingStructureBlock extends HorizontalDirectionalBlock implements EntityBlock {

    private final int tier;
    private final MapCodec<CraftingStructureBlock> instanceCodec;

    public CraftingStructureBlock(int tier, Properties properties) {
        super(properties);
        this.tier = tier;
        this.instanceCodec = simpleCodec(p -> new CraftingStructureBlock(tier, p));
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    public int tier() {
        return tier;
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return instanceCodec;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CraftingStructureBlockEntity(pos, state, tier);
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)
                || !(level.getBlockEntity(pos) instanceof CraftingStructureBlockEntity structure)) {
            return InteractionResult.SUCCESS;
        }
        serverPlayer.openMenu(structure);
        return InteractionResult.SUCCESS_SERVER;
    }
}
