package com.github.cerealklla.lyfe.rest;

import java.util.List;

import com.github.cerealklla.lyfe.registration.ModEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
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

    /**
     * The real stool block this seat belongs to -- confirmed via RCON against a live NPC's own NBT,
     * 2026-11-01: a negative {@code StoolBlock#SEAT_HEIGHT} (tried while tuning the floating-rider
     * report) silently broke sitting entirely, with no visible error -- {@link #tick}'s own
     * "is the stool still here" check used to derive the block to look at from {@code blockPosition()}
     * (this entity's own, height-adjustable position, floored), so a negative offset floored to the
     * block BELOW the real stool instead of the stool itself, failed the check, and self-discarded
     * (ejecting the rider) within a tick or two of ever mounting -- the rider then just stood normally
     * on the stool under ordinary gravity, explaining why no bent-leg pose ever showed despite nothing
     * being wrong with the pose logic itself. Storing the real block position explicitly, set whenever
     * this seat is created or reused, decouples correctness from {@code SEAT_HEIGHT} entirely -- that
     * constant can now move freely in either direction without risking this failure mode again.
     */
    private BlockPos stoolBlockPos;

    public SeatEntity(EntityType<? extends SeatEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static SeatEntity getOrCreate(ServerLevel level, BlockPos stoolPos) {
        double x = stoolPos.getX() + 0.5;
        double y = stoolPos.getY() + StoolBlock.SEAT_HEIGHT;
        double z = stoolPos.getZ() + 0.5;
        SeatEntity existing = findAt(level, stoolPos);
        if (existing != null) {
            // Real bug, caught 2026-11-01 during live tuning of SEAT_HEIGHT: a reused seat kept
            // whatever position it was first created at -- it was already live from an earlier
            // discard/respawn round (see TavernDailySpawnTicker#processStool, which routinely
            // discards an NPC and immediately re-seats a fresh one, often before this seat's own
            // tick() has had a chance to self-discard a now-passenger-less old instance) -- so a
            // later SEAT_HEIGHT change had no visible effect on any seat that happened to survive
            // reuse, while only a genuinely freshly-created one picked it up. This is exactly why
            // two height tweaks in a row looked like they did nothing for 4 of 5 stools, and why one
            // NPC sat visibly lower than the other four. Always refreshing position on reuse means a
            // code change to SEAT_HEIGHT takes effect everywhere the next time anything sits here,
            // not just on a seat's first-ever creation.
            existing.setPos(x, y, z);
            existing.stoolBlockPos = stoolPos;
            return existing;
        }
        SeatEntity seat = new SeatEntity(ModEntities.SEAT.get(), level);
        seat.setPos(x, y, z);
        seat.stoolBlockPos = stoolPos;
        level.addFreshEntity(seat);
        return seat;
    }

    /**
     * A generous, fixed-size box around the stool's own block position -- deliberately NOT scaled to
     * {@code StoolBlock#SEAT_HEIGHT} (an earlier version inflated only 0.1 around the block itself,
     * which could miss a seat entirely once SEAT_HEIGHT moved it far enough away) -- combined with the
     * exact {@link #stoolBlockPos} identity check below, this stays correct regardless of how far the
     * tunable height ever moves a seat from its stool.
     */
    public static SeatEntity findAt(ServerLevel level, BlockPos stoolPos) {
        AABB box = new AABB(stoolPos).inflate(1.5);
        List<SeatEntity> found = level.getEntitiesOfClass(SeatEntity.class, box, s -> stoolPos.equals(s.stoolBlockPos));
        return found.isEmpty() ? null : found.get(0);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        boolean stoolStillHere = stoolBlockPos != null && serverLevel.getBlockState(stoolBlockPos).getBlock() instanceof StoolBlock;
        if (!stoolStillHere || getPassengers().isEmpty()) {
            ejectPassengers();
            discard();
        }
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.storeNullable("StoolBlockPos", BlockPos.CODEC, stoolBlockPos);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        stoolBlockPos = input.read("StoolBlockPos", BlockPos.CODEC).orElse(null);
    }
}
