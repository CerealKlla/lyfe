package com.github.cerealklla.lyfe.summoning;

import java.util.Optional;
import java.util.UUID;

import com.github.cerealklla.lyfe.registration.ModItems;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Despawns a summoned mount or companion once its owner walks more than 20 blocks away (explicit
 * spec), mirrors {@code myssions.quest.QuestEscortTicker}'s own shape exactly: periodic, resolves
 * the tracked entity fresh via {@code getEntity(uuid)} every cycle rather than caching a raw
 * reference (avoids staleness across chunk unload), squared-distance comparison. Unlike a Guard Kit
 * Stand (a real placed-in-world entity, indexable), a Summoning Stone/Allay Bottle is a plain
 * {@code ItemStack} that can be anywhere in a player's inventory -- there's no world-level registry
 * to scan instead, so this scans each online player's own main inventory, same cost class as this
 * project's existing whole-inventory scans ({@code AutoDropper}/{@code sellToShop}).
 *
 * <p>Per the user's explicit choice: a despawn re-captures the entity's *current* state (gear, etc.)
 * back into the item rather than discarding it in favor of the original bind snapshot.
 *
 * <p><b>Real bug fix, 2026-10-29 -- this was the actual cause of a live duplication exploit</b>, see
 * {@link SummonDimensionChangeListener}'s own doc for the full write-up. {@link #resolveTracked}
 * used to call {@code player.level().getEntity(id)} only -- {@code ServerLevel#getEntity(UUID)} is
 * dimension-scoped, so the moment a player changed dimension (leaving their mount/companion behind
 * in the old one, same as vanilla's own un-ridden vehicles), the very next tick here would wrongly
 * conclude the entity was dead and silently clear the binding with no discard at all -- orphaning a
 * genuinely still-alive entity in the old dimension, permanently unbound, while leaving the stone
 * free to summon a brand new one immediately. Now searches every loaded dimension
 * ({@code MinecraftServer#getAllLevels()}) before ever concluding "dead," and only applies the
 * distance check at all when the entity is confirmed to be in the player's *own* current dimension
 * -- a cross-dimension leftover (which {@link SummonDimensionChangeListener} should already have
 * handled at the moment of transition) is left alone here rather than mishandled by a meaningless
 * cross-dimension distance comparison. Also widened to cover Allay Bottle companions, not just
 * mounts -- a real separate pre-existing gap: this ticker never checked {@link AllayBottleItem} at
 * all, so a summoned Allay companion never distance-despawned, full stop.
 */
public final class SummoningDistanceTicker {

    private static final int INTERVAL_TICKS = 20; // 1 second.
    private static final double DESPAWN_RADIUS_BLOCKS = 20.0;

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % INTERVAL_TICKS != 0) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!(player.level() instanceof ServerLevel level)) {
                continue;
            }
            var inventory = player.getInventory();
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack stack = inventory.getItem(i);
                if (stack.getItem() instanceof SummoningStoneItem) {
                    checkMount(server, level, player, stack);
                } else if (stack.getItem() instanceof AllayBottleItem) {
                    checkAllay(server, level, player, stack);
                }
            }
        }
    }

    /** {@code null} if genuinely not found in any loaded dimension -- distinct from "found, but in a different dimension than {@code expectedLevel}," which the caller must check separately before applying a distance comparison. */
    private static Entity resolveTracked(MinecraftServer server, UUID id) {
        for (ServerLevel candidate : server.getAllLevels()) {
            Entity found = candidate.getEntity(id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private void checkMount(MinecraftServer server, ServerLevel level, ServerPlayer player, ItemStack stack) {
        SummoningStoneData data = stack.get(ModItems.SUMMONING_STONE_DATA.get());
        if (data == null || data.summonedMountId().isEmpty()) {
            return;
        }
        Entity mount = resolveTracked(server, data.summonedMountId().get());
        if (mount == null || !mount.isAlive()) {
            stack.set(ModItems.SUMMONING_STONE_DATA.get(), data.withSummonedMountId(Optional.empty()));
            return;
        }
        if (mount.level() != level) {
            return; // A different dimension -- SummonDimensionChangeListener's job, not distance's.
        }
        double radiusSq = DESPAWN_RADIUS_BLOCKS * DESPAWN_RADIUS_BLOCKS;
        if (player.distanceToSqr(mount) <= radiusSq) {
            return;
        }

        SummoningStoneData updated = data.withSavedState(SummoningStoneData.capture(mount, level)).withSummonedMountId(Optional.empty());
        stack.set(ModItems.SUMMONING_STONE_DATA.get(), updated);
        mount.ejectPassengers();
        mount.discard();
    }

    private void checkAllay(MinecraftServer server, ServerLevel level, ServerPlayer player, ItemStack stack) {
        AllayBottleData data = stack.get(ModItems.ALLAY_BOTTLE_DATA.get());
        if (data == null || data.summonedAllayId().isEmpty()) {
            return;
        }
        Entity allay = resolveTracked(server, data.summonedAllayId().get());
        if (allay == null || !allay.isAlive()) {
            stack.set(ModItems.ALLAY_BOTTLE_DATA.get(), data.withSummonedAllayId(Optional.empty()));
            return;
        }
        if (allay.level() != level) {
            return;
        }
        double radiusSq = DESPAWN_RADIUS_BLOCKS * DESPAWN_RADIUS_BLOCKS;
        if (player.distanceToSqr(allay) <= radiusSq) {
            return;
        }

        AllayBottleData updated = data.withSavedState(SummoningStoneData.capture(allay, level)).withSummonedAllayId(Optional.empty());
        stack.set(ModItems.ALLAY_BOTTLE_DATA.get(), updated);
        allay.ejectPassengers();
        allay.discard();
    }
}
