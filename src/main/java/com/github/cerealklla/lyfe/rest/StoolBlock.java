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
     * How far above the block's own Y the {@link SeatEntity} mount point sits. Deliberately low, not
     * "roughly stool-seat height" as this used to say -- real report, 2026-11-01: a rider "floats
     * over the stool instead of sitting on it." Root cause, confirmed against the real decompiled
     * vanilla source: {@code HumanoidModel#setupAnim}'s {@code state.isPassenger} branch bends both
     * legs up to a fixed ~-81-degree angle at the hip regardless of vehicle, the same rotation used
     * for a horse's saddle -- the entity's own Y position is where the hip pivots from, and the bent
     * legs then occupy space ABOVE that point, not below it. At the old 0.3 (plus {@link SeatEntity}'s
     * own 0.2-high {@code EntityAttachment.PASSENGER} fallback point, for 0.5 total), the rider's hip
     * sat right at the stool's physical top with nothing bridging the gap down to it, reading as
     * hovering in midair above the stool. Vanilla's own {@code AbstractBoat#rideHeight} (the same
     * {@code isPassenger} pose, used for a boat seat) sets this to roughly 1/3 of the boat's own
     * height, deliberately low so the bent legs land near the hull's floor instead of its rim -- 0.0
     * here (0.2 total with the Seat's own fallback) copies that same ratio for a stool of comparable
     * size, so the bent-leg pose lands near the seat instead of floating well above it.
     */
    public static final double SEAT_HEIGHT = 0.0;

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
