package com.github.cerealklla.lyfe.repair;

import com.github.cerealklla.lyfe.structure.UpgradeCostEntry;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * The real material quantity to fully repair {@code stack} -- proportional to how much durability
 * is actually missing (2026-10-07 user spec), not a flat cost regardless of damage. A pristine item
 * costs nothing (0 units); a fully-zeroed item (including one clamped at {@code maxDamage - 1} by
 * {@code durability.mixin.ItemStackUnbreakableMixin}, which this still treats as "missing basically
 * all of it") costs the full {@link RepairMaterials#FULL_REPAIR_AMOUNT}.
 *
 * <p>Public (unlike the rest of this package) specifically so {@code client.RepairStructureScreen}
 * can preview the cost live as the player places an item in the slot -- pure client-side math off
 * the already-synced {@link ItemStack}, no networking needed.
 */
public final class RepairCost {

    private RepairCost() {
    }

    /** {@code null} if {@code stack} isn't one of {@code EquipmentTierLadder}'s own items, or isn't currently damaged at all. */
    static UpgradeCostEntry costFor(ItemStack stack) {
        RepairableItem.TierInfo info = RepairableItem.of(stack);
        if (info == null || !stack.isDamageableItem() || stack.getDamageValue() <= 0) {
            return null;
        }
        double missingFraction = stack.getDamageValue() / (double) stack.getMaxDamage();
        int amount = (int) Math.ceil(RepairMaterials.FULL_REPAIR_AMOUNT * missingFraction);
        return RepairMaterials.scaledEntryFor(info.tier(), info.armor(), stack.getItem(), Math.max(1, amount));
    }

    /** {@code null} if the slot is empty -- otherwise a one-line player-facing summary for the screen to show above the buttons. */
    public static Component describeFor(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        if (RepairableItem.of(stack) == null) {
            return Component.literal("This item can't be repaired here.");
        }
        UpgradeCostEntry cost = costFor(stack);
        if (cost == null) {
            return Component.literal("Already fully repaired.");
        }
        // The real item display name (e.g. "Leather"), not UpgradeCostEntry#label()'s own plain
        // registry-id form -- that's fine for the error messages elsewhere that reuse it (an
        // established, if slightly unpolished, convention -- see StructureUpgradeFunding), but a real
        // bug for player-facing UI text, found live 2026-10-07 ("Needs: 4x minecraft:leather").
        Component materialName = cost.itemId()
                .map(id -> new ItemStack(BuiltInRegistries.ITEM.getValue(id)).getHoverName())
                .orElseGet(() -> Component.literal(cost.label()));
        return Component.literal("Needs: " + cost.amount() + "x ").append(materialName);
    }
}
