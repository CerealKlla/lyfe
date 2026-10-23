package com.github.cerealklla.lyfe.fishing;

import java.lang.reflect.Field;
import java.util.UUID;

import com.github.cerealklla.lyfe.registration.ModEntities;
import com.github.cerealklla.lyfe.registration.ModMobEffects;
import com.github.cerealklla.nuclyus.ping.PingCooldown;
import com.github.cerealklla.nuclyus.ping.PingPacket;
import com.github.cerealklla.nuclyus.ping.Pingable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A permanent, fully-invisible {@link Pingable} marker placed once inside a naturally-generated
 * Shipwreck's own bounds (design doc Section 28, 2026-10-24) -- replaces the old catch-time
 * {@code StructureManager#getStructureWithPieceAt} re-check in {@link FishermanListener} (a real,
 * confirmed bug: that method never actually returns {@code null}, so the old {@code != null} check
 * was always true) with a ping-driven buff instead. Explicitly NOT a periodic server tick -- the
 * user's own rejected alternative.
 *
 * <p>Always rendered as {@link Blocks#BARRIER} -- genuinely invisible in normal survival rendering,
 * unlike a pale/clear carpet which would still show as a visible thin slab (same reasoning as
 * Settlemynts' own Roadway Connection Marker rework from the same day/conversation, see that mod's
 * own {@code roadway.RoadwayConnectionMarkerEntity} for the sibling feature -- not reused directly
 * since this feature has nothing to do with settlements and shouldn't need Settlemynts loaded at
 * all; same shape, separately implemented).
 *
 * <p><b>Permanent and indestructible</b> -- no "shown/hidden" visual state (unlike the road
 * markers, there's no reason to ever reveal this one to a player) and no removal path at all once
 * placed: {@link #isPickable()} is {@code false} (no attack/interaction hitbox to begin with) and
 * {@code setInvulnerable(true)} is set at creation on top of that (confirmed against the real
 * decompiled {@code Entity} source -- {@code isInvulnerableToBase(DamageSource)} checks exactly
 * this flag and is the real choke point every damage source funnels through) so nothing
 * (explosions, fire, etc.) can remove one outside this mod's own logic. Deliberately NOT {@code
 * .noSave()} -- this
 * entity must survive a normal world save/load like any other entity, since {@link
 * ShipwreckMarkerListener}'s own one-time-placement index would otherwise skip ever re-creating it
 * after a restart wiped an unsaved copy.
 */
public class ShipwreckMarkerEntity extends Display.BlockDisplay implements Pingable {

    private static final int PING_RANGE_BLOCKS = 15;
    private static final long PING_COOLDOWN_TICKS = 50L; // 2.5 seconds.
    private static final int BUFF_DURATION_TICKS = 100; // 5 seconds.

    private static volatile EntityDataAccessor<BlockState> blockStateAccessor;

    private final PingCooldown pingCooldown = new PingCooldown();

    public ShipwreckMarkerEntity(EntityType<? extends ShipwreckMarkerEntity> type, Level level) {
        super(type, level);
    }

    public static ShipwreckMarkerEntity create(ServerLevel level, BlockPos pos) {
        ShipwreckMarkerEntity marker = new ShipwreckMarkerEntity(ModEntities.SHIPWRECK_MARKER.get(), level);
        marker.setPos(pos.getX(), pos.getY(), pos.getZ());
        marker.setInvulnerable(true);
        setBlockState(marker, Blocks.BARRIER.defaultBlockState());
        level.addFreshEntity(marker);
        return marker;
    }

    @Override
    public void onPing(PingPacket ping) {
        if (ping.distanceToPlayer() > PING_RANGE_BLOCKS || !(level() instanceof ServerLevel level)) {
            return;
        }
        ServerPlayer player = ping.player();
        UUID playerId = player.getUUID();
        long now = level.getGameTime();
        if (!pingCooldown.isReady(playerId, now, PING_COOLDOWN_TICKS)) {
            return;
        }
        pingCooldown.markHandled(playerId, now);
        // Refreshes (not stacks) by construction -- vanilla's own addEffect replaces an existing
        // instance of the same effect at the same amplifier with the new, full-length one.
        player.addEffect(new MobEffectInstance(ModMobEffects.SHIPWRECK, BUFF_DURATION_TICKS, 0, false, false, true));
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public Component getDisplayName() {
        return Component.literal("Shipwreck Marker");
    }

    @SuppressWarnings("unchecked")
    private static void setBlockState(Display.BlockDisplay entity, BlockState state) {
        EntityDataAccessor<BlockState> accessor = blockStateAccessor;
        if (accessor == null) {
            synchronized (ShipwreckMarkerEntity.class) {
                accessor = blockStateAccessor;
                if (accessor == null) {
                    try {
                        Field field = Display.BlockDisplay.class.getDeclaredField("DATA_BLOCK_STATE_ID");
                        field.setAccessible(true);
                        accessor = (EntityDataAccessor<BlockState>) field.get(null);
                        blockStateAccessor = accessor;
                    } catch (ReflectiveOperationException e) {
                        throw new IllegalStateException("Could not resolve Display.BlockDisplay's block state accessor", e);
                    }
                }
            }
        }
        entity.getEntityData().set(accessor, state);
    }
}
