package com.github.cerealklla.lyfe.summoning;

import java.util.UUID;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;

/**
 * Pushes a bound Allay's Equip/Gather slots (design doc Section 27's Shift+Right-Click GUI) onto a
 * real, live {@link Allay} -- confirmed real design resolution, 2026-10-24, after finding vanilla's
 * own gather/"liked item" mechanic is hardcoded to {@code EquipmentSlot#MAINHAND} specifically
 * (confirmed against the real decompiled {@code Allay} source -- {@code hasItemInHand}/{@code
 * wantsToPickUp}/{@code canPickUpLoot} all key off {@code getItemInHand(MAIN_HAND)} directly, not
 * its generic inventory), which would otherwise collide with a mainhand-equipped weapon/tool:
 * <ul>
 *   <li><b>Gather</b> -- always {@link EquipmentSlot#MAINHAND}, exactly matching vanilla's own
 *   "liked item" slot, so vanilla's existing fetch-matching-ground-items behavior works completely
 *   unmodified, no extra AI code needed for it at all.</li>
 *   <li><b>Equip</b> -- resolved generically via the item's own {@link Equippable} data component
 *   (its declared slot, e.g. a real armor piece) if present, otherwise {@link
 *   EquipmentSlot#OFFHAND} (a weapon/tool, e.g. a Nature Staff -- matching {@code api.Lyfe
 *   #hasOffhandSpell}'s own existing off-hand-casting design, not a guess).</li>
 * </ul>
 */
public final class AllayEquipment {

    private AllayEquipment() {
    }

    public static void applyEquipAndGather(Allay allay, ItemStack equip, ItemStack gather) {
        allay.setItemSlot(resolveEquipSlot(equip), equip.copy());
        allay.setItemInHand(InteractionHand.MAIN_HAND, gather.copy());
        // "Any item it's holding will be saved upon death... and returned when it is re-summoned" --
        // the Equip/Gather items are already tracked independently in AllayBottleData regardless of
        // what happens to the live entity, so the only remaining risk is vanilla's own standard
        // equipment-drop-on-death mechanism *also* dropping a second, physical copy on top of that --
        // a real duplication bug. Zeroed here, same established fix Settlemynts' own
        // GuardEntity#equipFromLoadout already uses for an identical problem.
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            allay.setDropChance(slot, 0.0F);
        }
    }

    /** Also stamps the vanilla "liked player" memory so the Allay actually brings gathered items back to its owner, same as vanilla's own hand-fed-item flow already does. */
    public static void applyEquipAndGather(Allay allay, ItemStack equip, ItemStack gather, UUID ownerId) {
        applyEquipAndGather(allay, equip, gather);
        if (!gather.isEmpty()) {
            allay.getBrain().setMemory(MemoryModuleType.LIKED_PLAYER, ownerId);
        }
    }

    private static EquipmentSlot resolveEquipSlot(ItemStack equip) {
        if (equip.isEmpty()) {
            return EquipmentSlot.OFFHAND;
        }
        Equippable equippable = equip.get(DataComponents.EQUIPPABLE);
        return equippable != null ? equippable.slot() : EquipmentSlot.OFFHAND;
    }
}
