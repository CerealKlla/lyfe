package com.github.cerealklla.lyfe.summoning;

import java.util.Optional;
import java.util.UUID;

import com.github.cerealklla.lyfe.LyfeMod;
import com.github.cerealklla.lyfe.registration.ModItems;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.item.ItemStack;

/**
 * Lets a non-player holder (a Settlemynts Guard, via {@code bridge.LyfeSummoningBridge}) carry and
 * use a bound Allay in a Bottle / Summoning Stone -- explicit user spec, 2026-10-25: "Use it once
 * when spawning, and if the summon dies use it again when they come off cooldown." Deliberately
 * side-steps {@link AllayBottleItem#use}/{@link SummoningStoneItem#use} entirely rather than calling
 * them -- both require a real {@code ServerPlayer} (owner UUID, look angle, chat feedback), which a
 * Guard (a {@code PathfinderMob}) can never supply. Instead this mirrors their summon logic directly,
 * keyed to an arbitrary owner UUID + explicit position, and -- since {@link SummonedAllayOwner}/
 * {@link SummonedMountOwner}'s own death listeners only ever scan a real online player's inventory,
 * and would silently never find a Guard-held stack -- never tags the summoned entity with either at
 * all. {@link #tick} is itself both the summon trigger AND the death/cooldown tracker: called
 * periodically by the holder, it notices a previously-summoned companion has died (starts the
 * cooldown, mirroring {@link AllayDeathListener}/{@link SummonedMountDeathListener}) and, once
 * nothing is out and the cooldown has cleared, summons fresh -- the same rule covers both "first
 * summon right after spawning" and "re-summon once off cooldown" with no separate code path.
 */
public final class GuardCompanionSupport {

    private GuardCompanionSupport() {
    }

    public static boolean isCompanionItem(ItemStack stack) {
        return stack.getItem() instanceof AllayBottleItem || stack.getItem() instanceof SummoningStoneItem;
    }

    public static void tick(ServerLevel level, ItemStack stack, UUID ownerId, double x, double y, double z, float yRot) {
        if (stack.getItem() instanceof AllayBottleItem) {
            tickAllay(level, stack, x, y, z, yRot);
        } else if (stack.getItem() instanceof SummoningStoneItem) {
            tickMount(level, stack, x, y, z, yRot);
        }
    }

    private static void tickAllay(ServerLevel level, ItemStack stack, double x, double y, double z, float yRot) {
        AllayBottleData data = stack.get(ModItems.ALLAY_BOTTLE_DATA.get());
        if (data == null) {
            return;
        }
        if (data.summonedAllayId().isPresent()) {
            // Dimension-scoped getEntity bug, same as SummoningStoneItem/AllayBottleItem's own
            // 2026-10-30 fix -- a Guard holding this can itself cross dimensions
            // (GuardReturnToDimensionGoal), leaving its companion behind in a different one.
            Entity existing = SummoningDistanceTicker.resolveTracked(level.getServer(), data.summonedAllayId().get());
            if (existing instanceof Allay allay && allay.isAlive()) {
                return;
            }
            long cooldownEnd = System.currentTimeMillis() + AllayBottleItem.COOLDOWN_MILLIS;
            stack.set(ModItems.ALLAY_BOTTLE_DATA.get(),
                    data.withSummonedAllayId(Optional.empty()).withCooldownEndEpochMillis(cooldownEnd));
            return;
        }
        if (data.onCooldown(System.currentTimeMillis())) {
            return;
        }

        Entity spawned = spawnFromSavedState(level, data.savedState(), x, y, z, yRot);
        if (!(spawned instanceof Allay allay)) {
            return;
        }
        allay.setCustomName(Component.literal(data.displayName()));
        AllayEquipment.applyEquipAndGather(allay, data.equipItem(), data.gatherItem());
        AllayGoalInjectionListener.reassessSpellGoal(allay);
        stack.set(ModItems.ALLAY_BOTTLE_DATA.get(), data.withSummonedAllayId(Optional.of(allay.getUUID())));
    }

    private static void tickMount(ServerLevel level, ItemStack stack, double x, double y, double z, float yRot) {
        SummoningStoneData data = stack.get(ModItems.SUMMONING_STONE_DATA.get());
        if (data == null) {
            return;
        }
        if (data.summonedMountId().isPresent()) {
            // Dimension-scoped getEntity bug, same as SummoningStoneItem/AllayBottleItem's own
            // 2026-10-30 fix -- a Guard holding this can itself cross dimensions
            // (GuardReturnToDimensionGoal), leaving its companion behind in a different one.
            Entity existing = SummoningDistanceTicker.resolveTracked(level.getServer(), data.summonedMountId().get());
            if (existing != null && existing.isAlive()) {
                return;
            }
            long cooldownEnd = System.currentTimeMillis() + SummoningStoneItem.COOLDOWN_MILLIS;
            stack.set(ModItems.SUMMONING_STONE_DATA.get(),
                    data.withSummonedMountId(Optional.empty()).withCooldownEndEpochMillis(cooldownEnd));
            return;
        }
        if (data.onCooldown(System.currentTimeMillis())) {
            return;
        }

        Entity spawned = spawnFromSavedState(level, data.savedState(), x, y, z, yRot);
        if (spawned == null) {
            return;
        }
        stack.set(ModItems.SUMMONING_STONE_DATA.get(), data.withSummonedMountId(Optional.of(spawned.getUUID())));
    }

    private static Entity spawnFromSavedState(ServerLevel level, net.minecraft.nbt.CompoundTag savedState, double x, double y, double z, float yRot) {
        ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(() -> "guard_companion", LyfeMod.LOGGER);
        try {
            return EntityType.loadEntityRecursive(savedState, level, EntitySpawnReason.MOB_SUMMONED, entity -> {
                entity.snapTo(x, y, z, yRot, 0.0F);
                level.addFreshEntity(entity);
                return entity;
            });
        } finally {
            reporter.close();
        }
    }
}
