package com.github.cerealklla.lyfe.craft;

import com.github.cerealklla.lyfe.api.Lyfe;
import com.github.cerealklla.lyfe.skill.Skills;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Weapon;
import net.minecraft.world.item.equipment.ArmorType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

/**
 * The real mechanic behind using/wearing equipment above your unlocked tier (2026-10-06, user's
 * second-round redesign of this same night's feature): nothing is ever blocked any more (replacing
 * both the tool/weapon caution's original "advisory only" framing and armor's hard equip-block,
 * {@code EquipmentGateListener}, now deleted, and {@code gathering.GatheringListener}'s older
 * Axe/Pickaxe break-speed=0 gate, also removed) -- instead, every durability-damage event an
 * under-proficient piece of gear takes is multiplied via {@code ToolTierUnlocks#extraDamageMultiplier}.
 * Applies on top of vanilla's own normal durability loss by issuing one extra {@code
 * ItemStack#hurtAndBreak} call for the difference, rather than trying to intercept/scale vanilla's
 * own call (there is no event around {@code ItemStack#hurtAndBreak} itself to hook, confirmed
 * against the decompiled source -- durability loss is a plain method call, not event-driven).
 *
 * <p>Three independent cases, each firing on whichever event lets this run without double-counting
 * vanilla's own base damage:
 * <ul>
 *   <li><b>Tool/weapon breaking a block</b> -- {@link #onBlockDrops}, any held
 *   {@code EquipmentTierLadder.ToolType} item, base assumed as vanilla's standard 1 durability per
 *   block (confirmed via the decompiled {@code ToolItem#mineBlock}/{@code ItemStack#mineBlock}).</li>
 *   <li><b>Weapon hitting a living entity</b> -- {@link #onDamagePost}, reads the attacker's
 *   held item's real {@code Weapon} data component for its own {@code itemDamagePerAttack()} (every
 *   {@code ToolType} has one -- Axe/Pickaxe/Shovel/Hoe get it via {@code ToolMaterial
 *   #applyToolProperties}, Sword/Spear via {@code #applySwordProperties}, confirmed against the
 *   decompiled source), fires before vanilla's own {@code ItemStack#postHurtEnemy} call later in
 *   the same {@code Player#attack} invocation (traced in the decompiled source), so there's no
 *   double-count risk either way.</li>
 *   <li><b>Armor piece absorbing a hit</b> -- {@link #onIncomingDamage}, mirrors vanilla's own
 *   {@code LivingEntity#doHurtEquipment} formula ({@code max(1, rawDamage / 4)}) for the base,
 *   fires via {@code LivingIncomingDamageEvent} -- confirmed (decompiled source) to run before
 *   armor's own durability loss, which happens inside {@code actuallyHurt}'s armor-reduction step.
 *   Governed by Crafter level (no dedicated armor skill exists).</li>
 * </ul>
 */
public final class ProficiencyDurabilityListener {

    @SubscribeEvent
    public void onBlockDrops(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack tool = player.getMainHandItem();
        for (EquipmentTierLadder.ToolType toolType : EquipmentTierLadder.ToolType.values()) {
            int tier = EquipmentTierLadder.tierOfItem(toolType, tool.getItem());
            if (tier < 0) {
                continue;
            }
            int level = Lyfe.getLevel(player, EquipmentTierLadder.governingSkillId(toolType));
            applyExtra(player, tool, EquipmentSlot.MAINHAND, tier, level, 1);
            return;
        }
    }

    @SubscribeEvent
    public void onDamagePost(LivingDamageEvent.Post event) {
        if (event.getHealthDamage() <= 0) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker) || event.getEntity() == attacker) {
            return;
        }
        ItemStack weapon = attacker.getMainHandItem();
        Weapon weaponData = weapon.get(DataComponents.WEAPON);
        if (weaponData == null) {
            return;
        }
        for (EquipmentTierLadder.ToolType toolType : EquipmentTierLadder.ToolType.values()) {
            int tier = EquipmentTierLadder.tierOfItem(toolType, weapon.getItem());
            if (tier < 0) {
                continue;
            }
            int level = Lyfe.getLevel(attacker, EquipmentTierLadder.governingSkillId(toolType));
            applyExtra(attacker, weapon, EquipmentSlot.MAINHAND, tier, level, weaponData.itemDamagePerAttack());
            return;
        }
    }

    @SubscribeEvent
    public void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getAmount() <= 0) {
            return;
        }
        int baseArmorDamage = (int) Math.max(1.0F, event.getAmount() / 4.0F);
        int crafterLevel = Lyfe.getLevel(player, Skills.CRAFTER_ID);
        for (ArmorType armorType : EquipmentTierLadder.ARMOR_TYPES) {
            ItemStack piece = player.getItemBySlot(armorType.getSlot());
            if (piece.isEmpty()) {
                continue;
            }
            int tier = EquipmentTierLadder.tierOfArmorItem(armorType, piece.getItem());
            if (tier < 0) {
                continue;
            }
            applyExtra(player, piece, armorType.getSlot(), tier, crafterLevel, baseArmorDamage);
        }
    }

    private static void applyExtra(ServerPlayer player, ItemStack stack, EquipmentSlot slot, int tier, int level, int baseAmount) {
        double multiplier = ToolTierUnlocks.extraDamageMultiplier(tier, ToolTierUnlocks.unlockedTier(level));
        if (multiplier <= 0) {
            return;
        }
        int extra = (int) Math.round(baseAmount * multiplier);
        if (extra > 0) {
            stack.hurtAndBreak(extra, player, slot);
        }
    }
}
