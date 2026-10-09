package com.github.cerealklla.lyfe.reincarnation;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.github.cerealklla.lyfe.api.Lyfe;
import com.github.cerealklla.lyfe.registration.ModMobEffects;
import com.github.cerealklla.lyfe.skill.Skills;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gamerules.GameRules;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Reincarnation's event wiring — grants XP on every player death, and protects that player's
 * currently-unlocked slots (see {@link ReincarnationSlots}) from the normal death-drop. The first
 * {@code LivingDeathEvent}/{@code PlayerEvent.Clone} handler anywhere in this suite.
 *
 * <p>Protected items are pulled out of the dying player's inventory/equipment directly inside
 * {@link #onLivingDeath}, which NeoForge fires at the very start of {@code LivingEntity#die} —
 * before vanilla's own drop logic runs (confirmed against the real NeoForge source,
 * {@code CommonHooks#onLivingDeath}) — so clearing a slot here reliably means it's never dropped.
 * Since death replaces the {@code Player} with a brand-new entity instance (simply "not dropping"
 * an item doesn't carry it to the next life on its own), each cleared item is stashed in {@link
 * #pendingRestore}, a purely in-memory, per-player map, and handed back onto the new player
 * instance in {@link #onPlayerClone}.
 *
 * <p><b>Summoning Sickness, added 2026-10-08</b> (explicit user request) — every respawn applies
 * {@link ModMobEffects#SUMMONING_SICKNESS} for {@link #SUMMONING_SICKNESS_DURATION_TICKS} (5
 * minutes). Dying again while it's still active earns zero Reincarnation XP for that death (instead
 * of discouraging suicide-farming the per-death XP by repeatedly dying in quick succession), and the
 * next respawn shows a red warning explaining why. The warning can't be sent at the moment of death
 * itself — the new player instance a respawn message would target doesn't exist yet — so "no XP this
 * time" is stashed in {@link #pendingNoXpWarning} the same way {@link #pendingRestore} already stages
 * cross-death state, and consumed in {@link #onPlayerClone}.
 */
public final class ReincarnationListener {

    // Placeholder, flagged as tunable — same convention every other skill's XP amount already uses.
    private static final long XP_PER_DEATH = 50;

    private static final int SUMMONING_SICKNESS_DURATION_TICKS = 5 * 60 * 20;

    private final Map<UUID, Map<ProtectedSlot, ItemStack>> pendingRestore = new ConcurrentHashMap<>();
    private final Set<UUID> pendingNoXpWarning = java.util.concurrent.ConcurrentHashMap.newKeySet();

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity instanceof ServerPlayer player)) {
            return;
        }

        // keepInventory already keeps everything -- nothing for this mechanic to protect, and
        // clearing/restoring slots here would be pointless extra churn on an already-solved case.
        boolean keepInventory = player.level().getGameRules().get(GameRules.KEEP_INVENTORY);
        if (!keepInventory) {
            protectSlots(player);
        }

        if (player.hasEffect(ModMobEffects.SUMMONING_SICKNESS)) {
            pendingNoXpWarning.add(player.getUUID());
        } else {
            Lyfe.addXp(player, Skills.REINCARNATION_ID, XP_PER_DEATH);
        }
    }

    private void protectSlots(ServerPlayer player) {
        // Current level, read before this death's own XP is added -- this death's reward applies
        // starting next time, not retroactively to the death that earned it.
        int level = Lyfe.getLevel(player, Skills.REINCARNATION_ID);
        List<ProtectedSlot> protectedSlots = ReincarnationSlots.protectedSlotsForLevel(level);
        if (protectedSlots.isEmpty()) {
            return;
        }
        Map<ProtectedSlot, ItemStack> saved = new EnumMap<>(ProtectedSlot.class);
        for (ProtectedSlot slot : protectedSlots) {
            ItemStack stack = currentStack(player, slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (slot.isTypeFiltered() && !ItemClassification.isProtectableType(stack)) {
                continue;
            }
            saved.put(slot, stack.copy());
            applyStack(player, slot, ItemStack.EMPTY);
        }
        if (!saved.isEmpty()) {
            pendingRestore.put(player.getUUID(), saved);
        }
    }

    @SubscribeEvent
    public void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath() || !(event.getEntity() instanceof ServerPlayer newPlayer)) {
            return;
        }
        UUID originalId = event.getOriginal().getUUID();

        Map<ProtectedSlot, ItemStack> saved = pendingRestore.remove(originalId);
        if (saved != null) {
            saved.forEach((slot, stack) -> applyStack(newPlayer, slot, stack));
        }

        if (pendingNoXpWarning.remove(originalId)) {
            newPlayer.sendSystemMessage(Component.literal(
                    "No Reincarnation XP gained -- you died again while Summoning Sickness was still active.")
                    .withStyle(ChatFormatting.RED));
        }

        newPlayer.addEffect(new MobEffectInstance(ModMobEffects.SUMMONING_SICKNESS,
                SUMMONING_SICKNESS_DURATION_TICKS, 0, false, true, true));
    }

    private static ItemStack currentStack(ServerPlayer player, ProtectedSlot slot) {
        return slot.isEquipment() ? player.getItemBySlot(slot.equipmentSlot()) : player.getInventory().getItem(slot.hotbarIndex());
    }

    private static void applyStack(ServerPlayer player, ProtectedSlot slot, ItemStack stack) {
        if (slot.isEquipment()) {
            player.setItemSlot(slot.equipmentSlot(), stack);
        } else {
            player.getInventory().setItem(slot.hotbarIndex(), stack);
        }
    }
}
