package com.github.cerealklla.lyfe.companion;

import java.util.Optional;

import com.github.cerealklla.lyfe.registration.ModAttachments;
import com.github.cerealklla.lyfe.registration.ModItems;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * Starts a Companion Token's cooldown once its summoned companion dies -- "retained if the NPC
 * dies and given back when it respawns, just like Allays." Direct mirror of {@code
 * summoning.AllayDeathListener}; no re-capture needed here (its gear is still fully baked into the
 * token's existing {@code savedState} from the moment it was hired/last dispelled), same reasoning
 * as the Allay case.
 */
public final class CompanionDeathListener {

    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        var owner = event.getEntity().getExistingData(ModAttachments.SUMMONED_COMPANION_OWNER);
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
            if (!(stack.getItem() instanceof CompanionTokenItem)) {
                continue;
            }
            CompanionTokenData data = stack.get(ModItems.COMPANION_TOKEN_DATA.get());
            if (data == null || data.summonedCompanionId().isEmpty()
                    || !data.summonedCompanionId().get().equals(event.getEntity().getUUID())) {
                continue;
            }
            long cooldownEnd = System.currentTimeMillis() + CompanionTokenItem.COOLDOWN_MILLIS;
            stack.set(ModItems.COMPANION_TOKEN_DATA.get(),
                    data.withSummonedCompanionId(Optional.empty()).withCooldownEndEpochMillis(cooldownEnd));
            return;
        }
    }
}
