package com.github.cerealklla.lyfe.summoning;

import java.util.Optional;

import com.github.cerealklla.lyfe.registration.ModAttachments;
import com.github.cerealklla.lyfe.registration.ModItems;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * Starts a Summoning Stone's 5-minute wall-clock cooldown once its summoned mount dies (explicit
 * spec). The dying entity carries {@link SummonedMountOwner} (set by {@code SummoningStoneItem#use}
 * at summon time) -- scanning only that one player's inventory instead of every online player's,
 * same reasoning {@code SummoningDistanceTicker}'s own doc gives for why no world-level index exists
 * for this.
 */
public final class SummonedMountDeathListener {

    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        var owner = event.getEntity().getExistingData(ModAttachments.SUMMONED_MOUNT_OWNER);
        if (owner.isEmpty() || !(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner.get().playerId());
        if (player == null) {
            return;
        }
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!(stack.getItem() instanceof SummoningStoneItem)) {
                continue;
            }
            SummoningStoneData data = stack.get(ModItems.SUMMONING_STONE_DATA.get());
            if (data == null || data.summonedMountId().isEmpty()
                    || !data.summonedMountId().get().equals(event.getEntity().getUUID())) {
                continue;
            }
            long cooldownEnd = System.currentTimeMillis() + SummoningStoneItem.COOLDOWN_MILLIS;
            stack.set(ModItems.SUMMONING_STONE_DATA.get(),
                    data.withSummonedMountId(Optional.empty()).withCooldownEndEpochMillis(cooldownEnd));
            return;
        }
    }
}
