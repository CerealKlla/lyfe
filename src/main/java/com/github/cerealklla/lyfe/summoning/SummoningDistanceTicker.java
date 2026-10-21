package com.github.cerealklla.lyfe.summoning;

import java.util.Optional;

import com.github.cerealklla.lyfe.LyfeMod;
import com.github.cerealklla.lyfe.registration.ModItems;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Despawns a summoned mount once its owner walks more than 20 blocks away (explicit spec), mirrors
 * {@code myssions.quest.QuestEscortTicker}'s own shape exactly: periodic, resolves the tracked
 * entity fresh via {@code level.getEntity(uuid)} every cycle rather than caching a raw reference
 * (avoids staleness across chunk unload), squared-distance comparison. Unlike a Guard Kit Stand
 * (a real placed-in-world entity, indexable), a Summoning Stone is a plain {@code ItemStack} that
 * can be anywhere in a player's inventory -- there's no world-level registry to scan instead, so
 * this scans each online player's own main inventory, same cost class as this project's existing
 * whole-inventory scans ({@code AutoDropper}/{@code sellToShop}).
 *
 * <p>Per the user's explicit choice: a despawn re-captures the mount's *current* state (gear, etc.)
 * back into the stone rather than discarding it in favor of the original bind snapshot.
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
                    checkStack(level, player, stack);
                }
            }
        }
    }

    private void checkStack(ServerLevel level, ServerPlayer player, ItemStack stack) {
        SummoningStoneData data = stack.get(ModItems.SUMMONING_STONE_DATA.get());
        if (data == null || data.summonedMountId().isEmpty()) {
            return;
        }
        Entity mount = level.getEntity(data.summonedMountId().get());
        if (mount == null || !mount.isAlive()) {
            stack.set(ModItems.SUMMONING_STONE_DATA.get(), data.withSummonedMountId(Optional.empty()));
            return;
        }
        double radiusSq = DESPAWN_RADIUS_BLOCKS * DESPAWN_RADIUS_BLOCKS;
        if (player.distanceToSqr(mount) <= radiusSq) {
            return;
        }

        ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(mount.problemPath(), LyfeMod.LOGGER);
        try {
            TagValueOutput output = TagValueOutput.createWithContext(reporter, level.registryAccess());
            mount.saveWithoutId(output);
            SummoningStoneData updated = data.withSavedState(output.buildResult()).withSummonedMountId(Optional.empty());
            stack.set(ModItems.SUMMONING_STONE_DATA.get(), updated);
        } finally {
            reporter.close();
        }
        mount.ejectPassengers();
        mount.discard();
    }
}
