package com.github.cerealklla.lyfe.research;

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
 * The Research Bench (design doc Section 19.5) -- a single, untiered structure where a player
 * places an item and researches it to learn its recipe. Same shape as Settlemynts'
 * {@code GuardhouseBlock}: a plain player-placeable block whose {@code BlockEntity} is the
 * {@code MenuProvider}.
 */
public class ResearchBenchBlock extends HorizontalDirectionalBlock implements EntityBlock {

    public static final MapCodec<ResearchBenchBlock> CODEC = simpleCodec(ResearchBenchBlock::new);

    public ResearchBenchBlock(Properties properties) {
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
        return new ResearchBenchBlockEntity(pos, state);
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)
                || !(level.getBlockEntity(pos) instanceof ResearchBenchBlockEntity bench)) {
            return InteractionResult.SUCCESS;
        }
        serverPlayer.openMenu(bench);
        return InteractionResult.SUCCESS_SERVER;
    }
}
