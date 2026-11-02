package com.github.cerealklla.lyfe.rest;

import java.util.List;

import com.github.cerealklla.lyfe.registration.ModEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * An invisible mount point a player rides to appear seated on a {@link StoolBlock} (design doc,
 * 2026-10-03 user request). Built on {@code Display.BlockDisplay}, never given a block state so
 * nothing actually renders -- the same "ghost marker with no block state" trick
 * {@code settlemynts.rope.RopeAnchorEntity} already uses, purely to reuse vanilla's own existing
 * {@code DisplayRenderer} instead of writing a bespoke no-op renderer (see
 * {@link client.SeatEntityRenderer}).
 *
 * <p>Never /summon-able (see {@code ModEntities#SEAT}) and, in practice, almost never actually
 * persisted either -- it's a pure, ephemeral mount point, recreated on demand by {@link #getOrCreate}
 * and self-discarded the moment it has no rider or the stool beneath it is gone. It must stay
 * genuinely serializable, though ({@code EntityType.Builder#noSave()} was tried and reverted) --
 * vanilla's own {@code Entity#startRiding} silently refuses to mount anything onto a vehicle whose
 * {@code EntityType#canSerialize()} is false, which otherwise makes sitting a complete, silent no-op.
 */
public class SeatEntity extends Display.BlockDisplay {

    public SeatEntity(EntityType<? extends SeatEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static SeatEntity getOrCreate(ServerLevel level, BlockPos stoolPos) {
        SeatEntity existing = findAt(level, stoolPos);
        if (existing != null) {
            return existing;
        }
        SeatEntity seat = new SeatEntity(ModEntities.SEAT.get(), level);
        seat.setPos(stoolPos.getX() + 0.5, stoolPos.getY() + StoolBlock.SEAT_HEIGHT, stoolPos.getZ() + 0.5);
        level.addFreshEntity(seat);
        return seat;
    }

    public static SeatEntity findAt(ServerLevel level, BlockPos stoolPos) {
        AABB box = new AABB(stoolPos).inflate(0.1);
        List<SeatEntity> found = level.getEntitiesOfClass(SeatEntity.class, box);
        return found.isEmpty() ? null : found.get(0);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        boolean stoolStillHere = serverLevel.getBlockState(blockPosition()).getBlock() instanceof StoolBlock;
        if (!stoolStillHere || getPassengers().isEmpty()) {
            ejectPassengers();
            discard();
        }
    }

    @Override
    public boolean isPickable() {
        return false;
    }
}
