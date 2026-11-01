package com.github.cerealklla.lyfe.companion;

import com.github.cerealklla.lyfe.registration.ModItems;
import com.github.cerealklla.lyfe.summoning.GuardCompanionSupport;
import com.github.cerealklla.lyfe.summoning.SummoningDistanceTicker;
import com.github.cerealklla.lyfe.summoning.SummoningStoneData;
import com.github.cerealklla.lyfe.summoning.SummoningStoneItem;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Keeps a summoned Tavern Companion's own "Mount" slot (real user request, 2026-11-01: "add 1 more
 * slot on the companion where you are allowed to equip a summon stone, which the companion can use
 * to mount and ride around") summoned and ridden. Mirrors {@code summoning.SummoningDistanceTicker}'s
 * own periodic-scan-of-player-inventory shape, scanning for a {@link CompanionTokenItem} instead of
 * a directly-held Summoning Stone -- a Companion Token's own Mount slot is nested inside its item
 * data component, not a loose inventory stack, so no existing ticker already covers it.
 *
 * <p>Reuses {@code summoning.GuardCompanionSupport#tick}'s own mount summon/cooldown logic verbatim
 * (a Settlemynts Guard holding a Summoning Stone already works exactly this way) -- the only new
 * piece here is actually mounting the companion onto its summoned mount once both are alive and the
 * companion isn't already riding it. Deliberately doesn't handle every edge case beyond what the
 * existing cooldown/distance-despawn/death-listener mechanics already cover elsewhere (e.g. an
 * orphaned mount left behind by a dead companion) -- a known, accepted v1 scope, same footing as
 * every other first-pass mechanic in this suite.
 */
public final class CompanionMountTicker {

    private static final int INTERVAL_TICKS = 20; // 1 second, same cadence as SummoningDistanceTicker.

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
                if (stack.getItem() instanceof CompanionTokenItem) {
                    tickMountFor(server, level, stack);
                }
            }
        }
    }

    private void tickMountFor(MinecraftServer server, ServerLevel level, ItemStack tokenStack) {
        CompanionTokenData data = tokenStack.get(ModItems.COMPANION_TOKEN_DATA.get());
        if (data == null || data.summonedCompanionId().isEmpty() || data.mountItem().isEmpty()
                || !(data.mountItem().getItem() instanceof SummoningStoneItem)) {
            return;
        }
        Entity companion = SummoningDistanceTicker.resolveTracked(server, data.summonedCompanionId().get());
        if (companion == null || !companion.isAlive()) {
            return;
        }
        ItemStack mountStack = data.mountItem().copy();
        GuardCompanionSupport.tick(level, mountStack, companion.getUUID(),
                companion.getX(), companion.getY(), companion.getZ(), companion.getYRot());
        tokenStack.set(ModItems.COMPANION_TOKEN_DATA.get(), data.withMountItem(mountStack));

        SummoningStoneData mountData = mountStack.get(ModItems.SUMMONING_STONE_DATA.get());
        if (mountData == null || mountData.summonedMountId().isEmpty()) {
            return;
        }
        Entity mount = SummoningDistanceTicker.resolveTracked(server, mountData.summonedMountId().get());
        if (mount != null && mount.isAlive() && companion.getVehicle() != mount) {
            // Confirmed against the real decompiled Entity source -- no (Entity, boolean) overload
            // exists in this MC version, only the 1-arg (unforced) and this 3-arg form. force=true so
            // this works regardless of the mount's own vehicle-eligibility rules (same reasoning a
            // forced mount spawn elsewhere in vanilla already relies on).
            companion.startRiding(mount, true, true);
        }
    }
}
