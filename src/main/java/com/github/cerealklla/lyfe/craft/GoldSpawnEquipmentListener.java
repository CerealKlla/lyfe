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
 * <p>Hooked on {@link EntityJoinLevelEvent} rather than a dedicated spawn-only event, matching
 * {@code summoning.AllayGoalInjectionListener}'s own precedent in this mod, but gated to a genuine
 * fresh spawn only -- see this class's own "same day" correction below for why a pre-existing mob
 * reloading from a saved chunk is deliberately left untouched now, not self-corrected.
 *
 * <p><b>Real bug found and fixed, 2026-11-05: "This is the second time now that my gold tool has
 * changed to copper. I crafted these and have had them a long time."</b> {@link EntityJoinLevelEvent}
 * fires for a {@code Player} too -- on login, respawn, and every dimension change -- and this
 * listener had no check excluding one. So a player's own hand-crafted Tier 4 ("Mythical") Golden
 * tool/armor piece, built on the same real vanilla item id this ladder maps Gold to (see {@code
 * CraftingOverhaul}'s own "Gold-tier naming" entry), got silently swapped to a plain Tier 2 Copper
 * item every time they rejoined the level -- not a cosmetic downgrade, a real loss of their actual
 * crafted item and its Quality/attribute data, since {@link ItemStack#transmuteCopy} changes the
 * item type itself.
 *
 * <p><b>Correction, same day</b>: a flat {@code !(entity instanceof Player)} check stops the player
 * data loss, but the actual design intent (confirmed by the user) was always "this gear should never
 * spawn as Gold in the first place," not "silently correct it one tick after the fact, for anything
 * that happens to walk through the door." A bare {@code Player} check is also only ever a special
 * case of the real, general distinction this event already exposes: {@link
 * EntityJoinLevelEvent#loadedFromDisk()} is {@code true} for *any* entity re-entering the world from
 * already-persisted data (a relogging/respawning/dimension-changing player, same as a previously-
 * saved mob whose chunk simply reloads) and {@code false} only for a genuinely brand-new entity this
 * exact spawn just created -- confirmed against the real NeoForge source
 * ({@code event/entity/EntityJoinLevelEvent.java}/{@code FinalizeSpawnEvent.java}: {@code
 * EntityJoinLevelEvent} is the very next step after a fresh {@code Mob#finalizeSpawn} call, which is
 * what actually assigns spawn equipment via {@code populateDefaultEquipmentSlots} -- so for a genuine
 * new spawn, this correction lands before the entity has ever been added to any client's world at
 * all, matching "never spawn as Gold" in every observable sense). {@link #onJoin} now gates on {@code
 * !event.loadedFromDisk()} as the real fix, with the explicit {@code Player} exclusion kept alongside
 * it as a second, zero-cost, unambiguous safety net given how severe this exact bug class already
 * proved to be -- and now also skips the client side entirely, since spawn equipment is only ever
 * assigned server-side ({@link net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent}'s own
 * doc) and a client-received mob already arrives with whatever the server already corrected, making a
 * client-side re-check pure dead work.
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
        if (event.getLevel().isClientSide() || event.loadedFromDisk()) {
            return;
        }
        Entity entity = event.getEntity();
        if (!(entity instanceof LivingEntity living) || entity instanceof net.minecraft.world.entity.player.Player) {
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
