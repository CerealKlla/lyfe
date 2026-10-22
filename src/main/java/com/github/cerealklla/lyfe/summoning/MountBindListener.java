package com.github.cerealklla.lyfe.summoning;

import java.util.Optional;

import com.github.cerealklla.lyfe.registration.ModAttachments;
import com.github.cerealklla.lyfe.registration.ModItems;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The "Bind to Summoning Stone" server-side flow (design doc Section 26): a payload round trip
 * mirroring {@code recallcinite.RecallciniteListener}'s own bind-confirm shape -- validate, open a
 * confirm/naming screen, re-validate from scratch on the actual confirm, never trusting the client
 * reached the screen honestly. The requesting player must still be *riding the exact same entity*
 * at both steps, or the bind silently does nothing.
 *
 * <p>Discards the original ridden mount the instant a bind succeeds (explicit default -- otherwise
 * binding the same mount repeatedly would duplicate it for free with no cost at all). {@link
 * SummoningStoneData#capture} captures its complete state (type, equipment, a chested mount's own
 * inventory, temper/tame/owner, everything each class's own save-data chain already writes) with a
 * genuinely fresh identity guaranteed on every future summon -- see that method's own doc for a real
 * bug this fixed.
 */
public final class MountBindListener {

    private MountBindListener() {
    }

    private static Optional<LivingEntity> resolveRiddenMount(ServerPlayer player, int mountEntityId) {
        if (!(player.getVehicle() instanceof LivingEntity mount) || mount.getId() != mountEntityId) {
            return Optional.empty();
        }
        return Optional.of(mount);
    }

    public static void requestBind(ServerPlayer player, RequestMountBindPayload payload) {
        LivingEntity mount = resolveRiddenMount(player, payload.mountEntityId()).orElse(null);
        if (mount == null || mount.getExistingData(ModAttachments.SUMMONED_MOUNT_OWNER).isPresent()) {
            return;
        }
        PacketDistributor.sendToPlayer(player, new OpenMountBindNameScreenPayload(payload.mountEntityId()));
    }

    public static void confirmBind(ServerPlayer player, ConfirmMountBindPayload payload) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        LivingEntity mount = resolveRiddenMount(player, payload.mountEntityId()).orElse(null);
        if (mount == null) {
            player.sendSystemMessage(Component.literal("You're no longer riding that mount."));
            return;
        }
        // Never trust the client alone (the button is hidden client-side for this case too, see
        // ModAttachments#SUMMONED_MOUNT_OWNER's own doc) -- re-binding an already-summoned mount
        // would let a player duplicate it via a second, independent Summoning Stone.
        if (mount.getExistingData(ModAttachments.SUMMONED_MOUNT_OWNER).isPresent()) {
            player.sendSystemMessage(Component.literal("That mount is already bound to a Summoning Stone."));
            return;
        }
        String name = payload.name().isBlank() ? mount.getType().toShortString() : payload.name();
        mount.setCustomName(Component.literal(name));

        CompoundTag savedState = SummoningStoneData.capture(mount, level);

        mount.ejectPassengers();
        mount.discard();

        ItemStack stone = new ItemStack(ModItems.SUMMONING_STONE.get());
        stone.set(ModItems.SUMMONING_STONE_DATA.get(), new SummoningStoneData(savedState, 0L, Optional.empty()));
        if (!player.getInventory().add(stone)) {
            player.drop(stone, false);
        }
        player.sendSystemMessage(Component.literal("Bound \"" + name + "\" to a Summoning Stone."));
    }
}
