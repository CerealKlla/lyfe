package com.github.cerealklla.lyfe.summoning;

import java.util.Optional;

import com.github.cerealklla.lyfe.registration.ModItems;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Despawns (re-captures into the stone/bottle, same as a voluntary dispel) a player's own summoned
 * mount/companion the instant they change dimension -- explicit user request, 2026-10-29, reported
 * as a real duplication exploit: "when a player changes dimensions, any summon/companion they have
 * out should automatically be despawned... it keeps leaving one behind... the horse is not
 * despawning when I walk away and you're able to effectively dupe them."
 *
 * <p>Root cause of the dupe: nothing in this package ever handled a dimension change at all, and
 * {@link SummoningDistanceTicker} (the only existing despawn trigger, distance-based) resolves the
 * tracked entity via {@code player.level().getEntity(id)} -- {@code ServerLevel#getEntity(UUID)}
 * only ever searches *that one dimension's* loaded entities (the same cross-dimension gap already
 * hit once this session elsewhere in this suite -- see the root project's own standing lesson on
 * re-verifying this exact assumption). Vanilla mounts/vehicles don't follow a player through a
 * portal (only the rider teleports), so after a dimension change the real mount is left behind in
 * the *old* dimension -- the very next distance-ticker pass then runs {@code
 * level.getEntity(id)} against the player's *new* dimension, finds nothing, and concludes the mount
 * is simply dead, silently clearing {@code summonedMountId} with no discard at all. The real mount
 * is left alive and now completely unbound (nothing references its UUID anymore -- "no longer
 * bound properly to me"), and since the stone immediately reads as "nothing summoned," it can be
 * used again right away to summon a brand new one -- the dupe.
 *
 * <p>This listener fires at the moment of the actual transition, while the old dimension (where the
 * mount/companion genuinely still is) is still known -- {@link PlayerEvent.PlayerChangedDimensionEvent#getFrom()}
 * -- so the lookup is correct by construction instead of racing the distance ticker's own
 * wrong-dimension guess. Known limitation, honestly flagged rather than silently assumed away: this
 * only covers the real portal-travel path (confirmed fired via {@code ServerPlayer#teleport
 * (TeleportTransition)}, per this event's own doc) -- a respawn that happens to land in a different
 * dimension is a separate internal path and isn't confirmed to fire this same event.
 */
public final class SummonDimensionChangeListener {

    @SubscribeEvent
    public void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ServerLevel fromLevel = player.level().getServer().getLevel(event.getFrom());
        if (fromLevel == null) {
            return;
        }
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.getItem() instanceof SummoningStoneItem) {
                despawnMount(fromLevel, stack);
            } else if (stack.getItem() instanceof AllayBottleItem) {
                despawnAllay(fromLevel, stack);
            }
        }
    }

    private void despawnMount(ServerLevel fromLevel, ItemStack stack) {
        SummoningStoneData data = stack.get(ModItems.SUMMONING_STONE_DATA.get());
        if (data == null || data.summonedMountId().isEmpty()) {
            return;
        }
        Entity mount = fromLevel.getEntity(data.summonedMountId().get());
        if (mount == null || !mount.isAlive()) {
            stack.set(ModItems.SUMMONING_STONE_DATA.get(), data.withSummonedMountId(Optional.empty()));
            return;
        }
        SummoningStoneData updated = data.withSavedState(SummoningStoneData.capture(mount, fromLevel)).withSummonedMountId(Optional.empty());
        stack.set(ModItems.SUMMONING_STONE_DATA.get(), updated);
        mount.ejectPassengers();
        mount.discard();
    }

    private void despawnAllay(ServerLevel fromLevel, ItemStack stack) {
        AllayBottleData data = stack.get(ModItems.ALLAY_BOTTLE_DATA.get());
        if (data == null || data.summonedAllayId().isEmpty()) {
            return;
        }
        Entity allay = fromLevel.getEntity(data.summonedAllayId().get());
        if (allay == null || !allay.isAlive()) {
            stack.set(ModItems.ALLAY_BOTTLE_DATA.get(), data.withSummonedAllayId(Optional.empty()));
            return;
        }
        AllayBottleData updated = data.withSavedState(SummoningStoneData.capture(allay, fromLevel)).withSummonedAllayId(Optional.empty());
        stack.set(ModItems.ALLAY_BOTTLE_DATA.get(), updated);
        allay.ejectPassengers();
        allay.discard();
    }
}
