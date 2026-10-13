package com.github.cerealklla.lyfe.craft;

import net.minecraft.core.component.DataComponents;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;

/**
 * Stamps this ladder's custom durability overrides ({@link EquipmentTierLadder#baseDurabilityOverride})
 * and armor rebalance ({@link ArmorRebalance}) onto the real vanilla items' own *default* components
 * -- added 2026-10-13, real live report: a Gold Shovel found in a loot chest (Curse of Vanishing,
 * never crafted) still had vanilla's raw 32 max damage, wearing out even faster than a Wood tool,
 * despite Gold's base durability having been explicitly raised to 900 back on 2026-10-06. Root cause:
 * {@code CraftingStructureMenu#attemptCraft} only ever stamped the override onto the one {@code
 * ItemStack} it had just crafted -- any other copy of the same item (loot-table drops, villager
 * trades, structure chests, a debug/creative give, an older item saved before the override existed)
 * kept using vanilla's own unmodified default. Explicit user correction: "all items, generated or
 * otherwise, should be using my custom stats."
 *
 * <p>Fixed at the real source instead: {@link ModifyDefaultComponentsEvent} lets a mod change what
 * components a vanilla item starts with by default, for every single instance of it from then on --
 * so a Gold Shovel is now born with 900 max damage and a Gold Helmet is born with its rebalanced
 * {@link ArmorRebalance} attributes, regardless of how that stack came to exist. {@code
 * CraftingStructureMenu#attemptCraft}'s own per-craft {@code result.set(...)} calls are left in
 * place rather than removed -- now redundant for every tier this class also covers, but still the
 * only place {@code QualityApplier}'s level-scaled bonus needs to layer on top of, and harmless to
 * re-apply the same base value a second time.
 */
public final class EquipmentDefaultComponents {

    private EquipmentDefaultComponents() {
    }

    public static void onModifyDefaultComponents(ModifyDefaultComponentsEvent event) {
        for (EquipmentTierLadder.ToolType tool : EquipmentTierLadder.ToolType.values()) {
            for (int tier = EquipmentTierLadder.MIN_TIER; tier <= EquipmentTierLadder.MAX_TIER; tier++) {
                int override = EquipmentTierLadder.baseDurabilityOverride(tier);
                if (override <= 0) {
                    continue;
                }
                var itemId = EquipmentTierLadder.itemId(tool, tier);
                var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(itemId);
                event.modify(item, (builder, registries, resolvedItem) -> builder.set(DataComponents.MAX_DAMAGE, override));
            }
        }

        for (var armorType : EquipmentTierLadder.ARMOR_TYPES) {
            for (int tier = EquipmentTierLadder.MIN_TIER; tier <= EquipmentTierLadder.MAX_TIER; tier++) {
                var itemId = EquipmentTierLadder.armorItemId(armorType, tier);
                var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(itemId);
                var attributes = ArmorRebalance.attributesFor(armorType, tier);
                event.modify(item, (builder, registries, resolvedItem) -> builder.set(DataComponents.ATTRIBUTE_MODIFIERS, attributes));
            }
        }
    }
}
