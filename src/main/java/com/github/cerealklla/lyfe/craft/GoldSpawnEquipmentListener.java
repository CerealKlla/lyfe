package com.github.cerealklla.lyfe.craft;

import java.util.IdentityHashMap;
import java.util.Map;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.ArmorType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * Real follow-up report, 2026-10-30: "all these zombie and non zombie piglins are still spawning
 * with gold equipment," right after {@link com.github.cerealklla.lyfe.socket.PreSocketedGearInjector}'s
 * Gold-to-Copper loot-table swap shipped. That earlier fix only ever touched chest loot tables --
 * vanilla's Piglin/Zombified Piglin/Piglin Brute spawn equipment (a held Golden Sword/Axe/Crossbow,
 * occasional Golden armor) is set directly by {@code Mob#populateDefaultEquipmentSlots} at spawn
 * time, a completely separate vanilla code path that never touches a loot table at all -- so it was
 * always going to keep handing out Gold regardless of that fix. Same underlying reasoning as before
 * ({@link EquipmentTierLadder} repositions Gold from vanilla's weakest metal tier up to a near-top
 * Tier 4, between Iron and Diamond -- free Gold gear from any source is now overpowered), just a
 * different trigger point.
 *
 * <p>Generic over every {@link EquipmentTierLadder.ToolType}/{@link ArmorType} rather than
 * hand-listing "golden_sword, golden_axe, ..." -- any vanilla mob that ever spawns wielding/wearing
 * one of this ladder's real Tier 4 (Gold) items gets it swapped to the equivalent Tier 2 (Copper)
 * item, preserving the stack's own components (enchantments from {@code
 * populateDefaultEquipmentEnchantments}, etc.) via {@link ItemStack#transmuteCopy}. Not scoped to
 * Piglins specifically -- any other vanilla mob capable of spawning with/picking up Gold gear (e.g.
 * a Zombie that found dropped Golden armor on the ground) is covered by the same fix, for the same
 * reason.
 *
 * <p>Hooked on {@link EntityJoinLevelEvent} rather than a spawn-only event, matching {@code
 * summoning.AllayGoalInjectionListener}'s own precedent in this mod -- this also means an
 * already-existing Zombified Piglin from before this fix shipped self-corrects the next time its
 * chunk loads, with no need for a world wipe or manual cleanup.
 */
public final class GoldSpawnEquipmentListener {

    private static final int GOLD_TIER = 4;
    private static final int COPPER_TIER = 2;

    private static final Map<Item, Item> REPLACEMENTS = buildReplacements();

    private static Map<Item, Item> buildReplacements() {
        Map<Item, Item> replacements = new IdentityHashMap<>();
        for (EquipmentTierLadder.ToolType tool : EquipmentTierLadder.ToolType.values()) {
            Item gold = itemFor(EquipmentTierLadder.itemId(tool, GOLD_TIER));
            Item copper = itemFor(EquipmentTierLadder.itemId(tool, COPPER_TIER));
            if (gold != null && copper != null) {
                replacements.put(gold, copper);
            }
        }
        for (ArmorType armorType : EquipmentTierLadder.ARMOR_TYPES) {
            Item gold = itemFor(EquipmentTierLadder.armorItemId(armorType, GOLD_TIER));
            Item copper = itemFor(EquipmentTierLadder.armorItemId(armorType, COPPER_TIER));
            if (gold != null && copper != null) {
                replacements.put(gold, copper);
            }
        }
        return Map.copyOf(replacements);
    }

    private static Item itemFor(Identifier id) {
        Item item = BuiltInRegistries.ITEM.getValue(id);
        return item == Items.AIR ? null : item;
    }

    @SubscribeEvent
    public void onJoin(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof LivingEntity living)) {
            return;
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack current = living.getItemBySlot(slot);
            Item replacement = REPLACEMENTS.get(current.getItem());
            if (replacement != null) {
                living.setItemSlot(slot, current.transmuteCopy(replacement));
            }
        }
    }
}
