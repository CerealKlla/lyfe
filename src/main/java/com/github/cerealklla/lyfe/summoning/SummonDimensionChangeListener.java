package com.github.cerealklla.lyfe.summoning;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.github.cerealklla.lyfe.registration.ModItems;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Despawns (re-captures into the stone/bottle, same as a voluntary dispel) a player's own summoned
 * mount/companion the instant they change dimension -- explicit user request, 2026-10-29, reported
 * as a real duplication exploit: "when a player changes dimensions, any summon/companion they have
 * out should automatically be despawned... it keeps leaving one behind... the horse is not
 * despawning when I walk away and you're able to effectively dupe them."
 *
 * <p><b>Reworked twice the same day, two real follow-up reports.</b> First: "riding a horse through
 * a nether portal the horse seems to become unbound. I can then dupe the horse and have 2 walking
 * around" -- the first version of this fix only searched {@code
 * PlayerEvent.PlayerChangedDimensionEvent#getFrom()} (the old dimension), on the (wrong) assumption
 * that a vehicle never follows its rider through a portal.
 *
 * <p><b>Second, deeper root cause, found while fixing the first report</b> -- confirmed against the
 * real decompiled source (both {@code Entity#teleport}/{@code #teleportCrossDimension} and {@code
 * ServerPlayer#teleport}): when a player rides a vehicle through a portal, it's the *vehicle* whose
 * {@code teleportCrossDimension} runs, and -- crucially -- it calls {@code this.ejectPassengers()}
 * and then recursively teleports each passenger (the player) **before** doing anything else;
 * {@code ServerPlayer#teleport} fires {@code PlayerChangedDimensionEvent} at the very end of *that*
 * nested call. Only *after* that whole nested call returns does the vehicle's own code continue:
 * create a brand-new entity instance in the new dimension, {@code restoreFrom} the old one (which,
 * despite the confusing method name, really does copy the real UUID across -- confirmed the same
 * way {@link SummoningStoneData#capture}'s own doc already had to for an unrelated reason), remove
 * the old instance, and re-mount the player onto the new one. So at the exact instant this event
 * fires, the OLD mount instance is still genuinely alive in {@code getFrom()}'s dimension -- but
 * discarding it right then does nothing to stop the vehicle's own still-in-flight outer call from
 * unconditionally creating a brand-new instance (same UUID) in the new dimension moments later
 * anyway, since that outer call has no idea this listener did anything to the object it's still
 * holding a live reference to. That's the real "unbound" symptom: the stone's binding gets cleared
 * right on schedule, but a live, fully-functional, newly re-created horse with the same identity
 * still ends up standing right there under the player regardless.
 *
 * <p>Fixed by not acting synchronously inside the event at all -- the player's UUID is queued here,
 * and the actual despawn check runs on the very next {@link ServerTickEvent.Post}, by which point
 * the vehicle's own cross-dimension sequence (an entirely synchronous call chain within the same
 * tick, confirmed above) has fully completed either way: an un-ridden mount is still sitting, alone,
 * in the old dimension; a ridden one has finished being re-created in the new one, same UUID. Either
 * way, searching every loaded dimension ({@link SummoningDistanceTicker#resolveTracked}, shared
 * rather than re-duplicated) at that point finds the one real, fully-settled instance and discards
 * it unconditionally (no distance check -- this is a direct dimension-change despawn, not the
 * distance ticker's own separate, narrower job).
 *
 * <p>Known limitation, honestly flagged rather than silently assumed away: this listener only
 * covers the real portal-travel path (confirmed fired via {@code ServerPlayer#teleport
 * (TeleportTransition)}, per this event's own doc) -- a respawn that happens to land in a different
 * dimension is a separate internal path and isn't confirmed to fire this same event. {@code
 * SummoningDistanceTicker}'s own all-dimensions hardening is the real backstop for that case (and
 * for an un-ridden mount's own distance despawn in general), just without this listener's near-
 * immediate timing.
 */
public final class SummonDimensionChangeListener {

    private static final Set<UUID> pendingPlayerIds = ConcurrentHashMap.newKeySet();

    @SubscribeEvent
    public void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            pendingPlayerIds.add(player.getUUID());
            // Temporary diagnostic (2026-10-30, live report: "I observed 2 allays and horses next
            // to me" after riding/walking through a portal) -- logs the exact moment this fires and
            // what it saw, to catch a possible race rather than guess further. Remove once the real
            // cause is confirmed and fixed.
            com.github.cerealklla.lyfe.LyfeMod.LOGGER.info(
                    "[LagDiagnostics] SummonDimensionChangeListener: {} changed dimension {} -> {}, queued for next-tick despawn check",
                    player.getName().getString(), event.getFrom().identifier(), event.getTo().identifier());
        }
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (pendingPlayerIds.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        for (UUID playerId : Set.copyOf(pendingPlayerIds)) {
            pendingPlayerIds.remove(playerId);
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) {
                continue; // Logged out mid-transition -- nothing left to despawn for.
            }
            var inventory = player.getInventory();
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack stack = inventory.getItem(i);
                if (stack.getItem() instanceof SummoningStoneItem) {
                    despawnMount(server, stack);
                } else if (stack.getItem() instanceof AllayBottleItem) {
                    despawnAllay(server, stack);
                }
            }
        }
    }

    private void despawnMount(MinecraftServer server, ItemStack stack) {
        SummoningStoneData data = stack.get(ModItems.SUMMONING_STONE_DATA.get());
        if (data == null || data.summonedMountId().isEmpty()) {
            return;
        }
        UUID id = data.summonedMountId().get();
        Entity mount = SummoningDistanceTicker.resolveTracked(server, id);
        if (mount == null || !mount.isAlive() || !(mount.level() instanceof ServerLevel mountLevel)) {
            com.github.cerealklla.lyfe.LyfeMod.LOGGER.info(
                    "[LagDiagnostics] SummonDimensionChangeListener.despawnMount: id {} not resolvable/alive (found={}) -- just clearing the binding",
                    id, mount);
            stack.set(ModItems.SUMMONING_STONE_DATA.get(), data.withSummonedMountId(Optional.empty()));
            return;
        }
        com.github.cerealklla.lyfe.LyfeMod.LOGGER.info(
                "[LagDiagnostics] SummonDimensionChangeListener.despawnMount: discarding mount {} (id {}) in {} at {}",
                mount, id, mountLevel.dimension().identifier(), mount.position());
        SummoningStoneData updated = data.withSavedState(SummoningStoneData.capture(mount, mountLevel)).withSummonedMountId(Optional.empty());
        stack.set(ModItems.SUMMONING_STONE_DATA.get(), updated);
        mount.ejectPassengers();
        mount.discard();
    }

    private void despawnAllay(MinecraftServer server, ItemStack stack) {
        AllayBottleData data = stack.get(ModItems.ALLAY_BOTTLE_DATA.get());
        if (data == null || data.summonedAllayId().isEmpty()) {
            return;
        }
        UUID id = data.summonedAllayId().get();
        Entity allay = SummoningDistanceTicker.resolveTracked(server, id);
        if (allay == null || !allay.isAlive() || !(allay.level() instanceof ServerLevel allayLevel)) {
            com.github.cerealklla.lyfe.LyfeMod.LOGGER.info(
                    "[LagDiagnostics] SummonDimensionChangeListener.despawnAllay: id {} not resolvable/alive (found={}) -- just clearing the binding",
                    id, allay);
            stack.set(ModItems.ALLAY_BOTTLE_DATA.get(), data.withSummonedAllayId(Optional.empty()));
            return;
        }
        com.github.cerealklla.lyfe.LyfeMod.LOGGER.info(
                "[LagDiagnostics] SummonDimensionChangeListener.despawnAllay: discarding allay {} (id {}) in {} at {}",
                allay, id, allayLevel.dimension().identifier(), allay.position());
        AllayBottleData updated = data.withSavedState(SummoningStoneData.capture(allay, allayLevel)).withSummonedAllayId(Optional.empty());
        stack.set(ModItems.ALLAY_BOTTLE_DATA.get(), updated);
        allay.ejectPassengers();
        allay.discard();
    }
}
