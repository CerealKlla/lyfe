package com.github.cerealklla.lyfe.summoning;

import java.util.List;
import java.util.Optional;

import com.github.cerealklla.lyfe.registration.ModItems;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * Builds a ready-to-use Summoning Stone, already bound to a synthetic tamed/saddled horse, for
 * Kyt's mod-managed Dev Kyt -- explicit user request, 2026-10-25, so testing Caryvan's new
 * Phase 2 (animal-drawn carts) doesn't require first manually taming/saddling/binding a real horse.
 *
 * <p>No normal binding flow exists for this (the real one, {@link MountBindListener}, requires the
 * player to already be riding a tamed mount), so this constructs a throwaway {@link Horse} purely
 * to capture its state via {@link SummoningStoneData#capture} -- the exact same capture path the
 * real bind flow uses, so the resulting stone is indistinguishable from a normally-bound one. The
 * horse is never added to the world ({@code addFreshEntity} is never called), so there's no real
 * spawn/despawn side effect, just a transient object used for its NBT.
 *
 * <p>{@code api.Kyt#registerDevKytContribution} takes a plain no-argument supplier with no level
 * reference -- {@link ServerLifecycleHooks#getCurrentServer()} is this suite's first use of that
 * NeoForge utility, needed specifically because the supplier runs lazily (at {@code /kyt getDev}
 * time, not at mod-init), by which point a real running server unquestionably exists.
 *
 * <p>Grants **two** independently-bound stones, not one, as of Caryvan's 2-horse Wagon (2026-10-25)
 * -- each call to {@link #buildOne} creates its own fresh throwaway {@link Horse} (a new random UUID
 * per instance, same as any other spawned entity), so two calls naturally produce two genuinely
 * distinct mount identities for testing a multi-mount cart without needing to tame/saddle/bind two
 * real horses by hand. A single-mount Animal Cart just gets a spare second stone, harmless.
 */
public final class DevSummoningStoneContribution {

    private DevSummoningStoneContribution() {
    }

    public static List<ItemStack> build() {
        ItemStack first = buildOne();
        if (first.isEmpty()) {
            return List.of();
        }
        ItemStack second = buildOne();
        return second.isEmpty() ? List.of(first) : List.of(first, second);
    }

    private static ItemStack buildOne() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return ItemStack.EMPTY;
        }
        ServerLevel level = server.overworld();
        Horse horse = EntityType.HORSE.create(level, EntitySpawnReason.MOB_SUMMONED);
        if (horse == null) {
            return ItemStack.EMPTY;
        }
        horse.setTamed(true);
        horse.setItemSlot(EquipmentSlot.SADDLE, new ItemStack(Items.SADDLE));
        String name = "Dev Test Horse";
        horse.setCustomName(Component.literal(name));

        CompoundTag savedState = SummoningStoneData.capture(horse, level);

        ItemStack stone = new ItemStack(ModItems.SUMMONING_STONE.get());
        stone.set(ModItems.SUMMONING_STONE_DATA.get(), new SummoningStoneData(savedState, 0L, Optional.empty(), name));
        return stone;
    }
}
