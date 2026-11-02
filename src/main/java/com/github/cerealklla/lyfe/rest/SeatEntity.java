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

    /**
     * Real report, 2026-11-01 (with a screenshot): lowering {@link StoolBlock#SEAT_HEIGHT} alone did
     * NOT fix the floating look -- confirmed live, the rider was still clearly hovering above the
     * stool with legs bent. Re-checked against {@code net.neoforged.neoforge.common.extensions.
     * IEntityExtension#shouldRiderSit} (defaults {@code true} for any vehicle) and {@code
     * HumanoidModel#setupAnim}'s {@code isPassenger} branch again: that branch rotates the ENTIRE leg
     * (thigh through foot) up from the hip by a fixed ~-81 degrees regardless of seat height -- no
     * part of the model ever extends back down to the entity's own Y position once that rotation
     * applies, so there is no seat height that avoids a visible gap; only disabling the pose does.
     * Overriding this to {@code false} makes the rider stand normally instead (straight legs, feet at
     * this entity's own Y) -- combined with {@link StoolBlock#SEAT_HEIGHT} putting that Y right at the
     * stool's physical top, the rider's feet now land directly on the visible stool surface instead of
     * floating above it.
     */
    @Override
    public boolean shouldRiderSit() {
        return false;
    }
}
