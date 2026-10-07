package com.github.cerealklla.lyfe.gathering;

import com.github.cerealklla.lyfe.api.Lyfe;
import com.github.cerealklla.lyfe.craft.EquipmentTierLadder;
import com.github.cerealklla.lyfe.skill.Skills;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

/**
 * Grants Excavator XP on breaking a shovel-appropriate block ({@code BlockTags.MINEABLE_WITH_SHOVEL})
 * while holding a Shovel (2026-10-06, user request) -- mirrors Farmer/Lumberjack/Miner's own
 * gathering-context XP shape exactly. Excavator's only real job right now is feeding
 * craft.EquipmentGateListener's Shovel tier-unlock check; no passive bonuses designed yet.
 */
public final class ExcavatorListener {

    private static final long XP_PER_BLOCK = 3;

    @SubscribeEvent
    public void onBlockDrops(BlockDropsEvent event) {
        if (!event.getState().is(BlockTags.MINEABLE_WITH_SHOVEL)) {
            return;
        }
        Entity breaker = event.getBreaker();
        if (!(breaker instanceof Player player)) {
            return;
        }
        ItemStack tool = player.getMainHandItem();
        if (EquipmentTierLadder.tierOfItem(EquipmentTierLadder.ToolType.SHOVEL, tool.getItem()) < 0) {
            return;
        }
        Lyfe.addXp(player, Skills.EXCAVATOR_ID, XP_PER_BLOCK);
    }
}
