package com.github.cerealklla.lyfe.fishing;

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
 * "Clean a fish" (design doc, 2026-10-03 user request) -- right-click to open a real GUI (rebuilt
 * 2026-10-05, replacing the original one-click {@code useItemOn} interaction -- user feedback after
 * finding no GUI appeared: "let's make it show a UI with 1 slot ... and 2 buttons"). Same shape as
 * {@code research.ResearchBenchBlock}: a plain player-placeable block whose {@code BlockEntity} is
 * the {@code MenuProvider}.
 */
public class FishCleaningStationBlock extends HorizontalDirectionalBlock implements EntityBlock {

    public static final MapCodec<FishCleaningStationBlock> CODEC = simpleCodec(FishCleaningStationBlock::new);

    public FishCleaningStationBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FishCleaningStationBlockEntity(pos, state);
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)
                || !(level.getBlockEntity(pos) instanceof FishCleaningStationBlockEntity station)) {
            return InteractionResult.SUCCESS;
        }
        serverPlayer.openMenu(station);
        return InteractionResult.SUCCESS_SERVER;
    }
}
