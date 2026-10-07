package com.github.cerealklla.lyfe.cook;

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
 * One tier (1-5) of the cooking-structure ladder (design doc Section 19.6): Cooking Station, Grill,
 * Stove, Oven, Chef Set -- direct structural mirror of {@code craft.CraftingStructureBlock}. All 5
 * are new custom blocks rather than hijacking vanilla's real {@code Blocks.CAMPFIRE} (which has its
 * own fixed mechanics and can't host a custom BlockEntity).
 */
public class CookingStructureBlock extends HorizontalDirectionalBlock implements EntityBlock {

    private final int tier;
    private final MapCodec<CookingStructureBlock> instanceCodec;

    public CookingStructureBlock(int tier, Properties properties) {
        super(properties);
        this.tier = tier;
        this.instanceCodec = simpleCodec(p -> new CookingStructureBlock(tier, p));
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
        return new CookingStructureBlockEntity(pos, state, tier);
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)
                || !(level.getBlockEntity(pos) instanceof CookingStructureBlockEntity structure)) {
            return InteractionResult.SUCCESS;
        }
        serverPlayer.openMenu(structure);
        return InteractionResult.SUCCESS_SERVER;
    }
}
