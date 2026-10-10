package com.github.cerealklla.lyfe.research;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntFunction;

import com.github.cerealklla.lyfe.craft.EquipmentTierLadder;
import com.github.cerealklla.lyfe.registration.ModAttachments;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.ArmorType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Passive Researcher mechanic (user request, 2026-10-09): every time an equipped tool, weapon, or
 * armor piece on {@link EquipmentTierLadder}'s ladder takes real durability damage, there's a flat
 * {@link ResearcherConstants#DURABILITY_RESEARCH_CHANCE} chance to grant {@link
 * ResearcherConstants#PASSIVE_DURABILITY_RESEARCH_POINTS} Research Point(s) -- toward the item
 * itself if its recipe isn't known yet, otherwise toward the next tier up. No-op once both the
 * current item and the next tier up are already known, or the current item is already at the
 * ladder's highest currently-reachable tier (Legendary/Tier 6 is dormant, see
 * {@link EquipmentTierLadder#MAX_REACHABLE_TIER}'s own doc -- nothing can use research progress
 * toward it yet).
 *
 * <p>Hooks the same three vanilla durability-loss signals {@code craft.ProficiencyDurabilityListener}
 * already uses (no event exists around {@code ItemStack#hurtAndBreak} itself, confirmed there against
 * the decompiled source) -- block-breaking, a weapon landing a hit, and armor absorbing a hit --
 * rather than only the extra proficiency-penalty damage that listener separately adds on top.
 */
public final class PassiveDurabilityResearchListener {

    @SubscribeEvent
    public void onBlockDrops(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack tool = player.getMainHandItem();
        for (EquipmentTierLadder.ToolType toolType : EquipmentTierLadder.ToolType.values()) {
            int tier = EquipmentTierLadder.tierOfItem(toolType, tool.getItem());
            if (tier >= 0) {
                roll(player, tier, t -> EquipmentTierLadder.itemId(toolType, t));
                return;
            }
        }
    }

    @SubscribeEvent
    public void onDamagePost(LivingDamageEvent.Post event) {
        if (event.getHealthDamage() <= 0
                || !(event.getSource().getEntity() instanceof ServerPlayer attacker)
                || event.getEntity() == attacker) {
            return;
        }
        ItemStack weapon = attacker.getMainHandItem();
        for (EquipmentTierLadder.ToolType toolType : EquipmentTierLadder.ToolType.values()) {
            int tier = EquipmentTierLadder.tierOfItem(toolType, weapon.getItem());
            if (tier >= 0) {
                roll(attacker, tier, t -> EquipmentTierLadder.itemId(toolType, t));
                return;
            }
        }
    }

    @SubscribeEvent
    public void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getAmount() <= 0) {
            return;
        }
        for (ArmorType armorType : EquipmentTierLadder.ARMOR_TYPES) {
            ItemStack piece = player.getItemBySlot(armorType.getSlot());
            if (piece.isEmpty()) {
                continue;
            }
            int tier = EquipmentTierLadder.tierOfArmorItem(armorType, piece.getItem());
            if (tier >= 0) {
                roll(player, tier, t -> EquipmentTierLadder.armorItemId(armorType, t));
            }
        }
    }

    private void roll(ServerPlayer player, int tier, IntFunction<Identifier> idAtTier) {
        if (ThreadLocalRandom.current().nextDouble() >= ResearcherConstants.DURABILITY_RESEARCH_CHANCE) {
            return;
        }

        var research = player.getData(ModAttachments.PLAYER_RESEARCH);
        boolean currentKnown = tier == 0 || research.isLearned(idAtTier.apply(tier));

        int targetTier;
        if (!currentKnown) {
            targetTier = tier;
        } else if (tier < EquipmentTierLadder.MAX_REACHABLE_TIER) {
            targetTier = tier + 1;
            if (research.isLearned(idAtTier.apply(targetTier))) {
                return;
            }
        } else {
            return;
        }

        Identifier targetId = idAtTier.apply(targetTier);
        int threshold = ResearcherConstants.thresholdForTier(targetTier);
        int oldPoints = research.researchPoints(targetId);
        if (!research.addResearchPoints(targetId, ResearcherConstants.PASSIVE_DURABILITY_RESEARCH_POINTS, threshold)) {
            return;
        }
        int newPoints = research.isLearned(targetId) ? threshold : research.researchPoints(targetId);
        PacketDistributor.sendToPlayer(player, new ResearchProgressPayload(targetId, oldPoints, newPoints, threshold));
    }
}
