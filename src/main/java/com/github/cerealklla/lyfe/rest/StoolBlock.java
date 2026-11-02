package com.github.cerealklla.lyfe.rest;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A simple sit-able stool (design doc, 2026-10-03 user request, "first pass") -- right-click to sit
 * (mounts a {@link SeatEntity}), right-click again or sneak to get up (vanilla's own generic
 * sneak-to-dismount, {@code Player#rideTick}, already handles the latter). Sitting counts toward
 * the same rest session {@link BedRestListener} already tracks for beds, so sitting on a stool
 * grants Well Rested the same way, scaled to how long you sat.
 */
public class StoolBlock extends Block {

    private static final VoxelShape SHAPE = Block.box(4, 0, 4, 12, 8, 12);

    /**
     * How far above the block's own Y the {@link SeatEntity} mount point sits -- roughly stool-seat
     * height. Back to 0.3 (plus {@link SeatEntity}'s own 0.2-high {@code EntityAttachment.PASSENGER}
     * fallback point, for 0.5 total -- the stool's own physical top), after a lower value alone was
     * confirmed live NOT to fix the floating-rider report -- see {@link SeatEntity#shouldRiderSit}'s
     * own doc for the real fix (disabling vanilla's bent-leg riding pose entirely) and why no height
     * value could have worked around that pose by itself.
     */
    public static final double SEAT_HEIGHT = 0.3;

    public StoolBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        SeatEntity seat = SeatEntity.getOrCreate(serverLevel, pos);
        if (player.getVehicle() == seat) {
            player.stopRiding();
            return InteractionResult.SUCCESS;
        }
        if (player.isPassenger() || !seat.getPassengers().isEmpty()) {
            return InteractionResult.PASS;
        }
        player.startRiding(seat);
        return InteractionResult.SUCCESS;
    }
}
