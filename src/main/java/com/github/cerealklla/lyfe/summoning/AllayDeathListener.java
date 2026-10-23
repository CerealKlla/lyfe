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
 * Starts an Allay in a Bottle's 5-minute wall-clock cooldown once its summoned Allay dies (design
 * doc Section 27, explicit spec: "If it dies, it has a 5 minute respawn cooldown") -- direct mirror
 * of {@link SummonedMountDeathListener}. No re-capture needed here (unlike a voluntary dispel) --
 * the Equip/Gather items are already tracked independently in {@link AllayBottleData}, kept correct
 * by {@code AllayBottleMenu#removed()} regardless of what happens to the live entity, and {@link
 * AllayEquipment#applyEquipAndGather} already zeroes every slot's drop chance so they're never also
 * physically dropped on top of that.
 */
public final class AllayDeathListener {

    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        var owner = event.getEntity().getExistingData(ModAttachments.SUMMONED_ALLAY_OWNER);
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
            if (!(stack.getItem() instanceof AllayBottleItem)) {
                continue;
            }
            AllayBottleData data = stack.get(ModItems.ALLAY_BOTTLE_DATA.get());
            if (data == null || data.summonedAllayId().isEmpty()
                    || !data.summonedAllayId().get().equals(event.getEntity().getUUID())) {
                continue;
            }
            long cooldownEnd = System.currentTimeMillis() + AllayBottleItem.COOLDOWN_MILLIS;
            stack.set(ModItems.ALLAY_BOTTLE_DATA.get(),
                    data.withSummonedAllayId(Optional.empty()).withCooldownEndEpochMillis(cooldownEnd));
            return;
        }
    }
}
