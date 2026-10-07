package com.github.cerealklla.lyfe.craft;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.ArmorType;

/**
 * Armor's "make the tier number actually mean something" defense rebalance (design doc Section
 * 19.4, armor added 2026-10-04) -- vanilla's own real per-material defense values don't increase
 * monotonically across this mod's tier order (Copper is weaker than Chainmail in every slot; Gold
 * is weaker than Iron), so a freshly-crafted armor piece gets a custom {@link
 * ItemAttributeModifiers} baked onto the stack at craft time (same "bake a bonus onto a crafted
 * stack" precedent as {@link QualityApplier}'s durability boost), completely overriding vanilla's
 * default modifiers for that item rather than stacking with them.
 *
 * <p>Exact point values are placeholder/tunable, same convention as every other numeric constant
 * in this mod -- the design doc explicitly left them "TBD at implementation time." Shaped off
 * vanilla's own real Iron armor (boots 2 / legs 5 / chest 6 / helm 2) scaled by a per-tier
 * multiplier, so the relative shape between pieces still feels like vanilla armor, just scaled to
 * a monotonically increasing per-tier total. Toughness is deliberately bumped at Gold (Tier 4,
 * +1.0) specifically to guarantee it out-defends Iron (Tier 3, +0.0 toughness) even though Iron's
 * raw armor points alone would already do that here -- a belt-and-suspenders fix for the exact
 * problem the design doc called out by name.
 */
final class ArmorRebalance {

    // [tier][0=boots,1=legs,2=chest,3=helm]
    private static final int[][] ARMOR_POINTS = {
            {1, 3, 4, 1},   // Tier 0 - Leather
            {2, 4, 5, 2},   // Tier 1 - Chainmail
            {2, 5, 6, 2},   // Tier 2 - Copper
            {2, 6, 7, 2},   // Tier 3 - Iron
            {3, 7, 8, 3},   // Tier 4 - Gold
            {3, 9, 10, 3},  // Tier 5 - Diamond
            {4, 10, 12, 4}  // Tier 6 - Netherite (Legendary)
    };

    private static final float[] TOUGHNESS_BY_TIER = {0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 2.0f, 3.0f};
    private static final float[] KNOCKBACK_RESISTANCE_BY_TIER = {0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.1f};

    private ArmorRebalance() {
    }

    static ItemAttributeModifiers attributesFor(ArmorType armorType, int tier) {
        int defense = ARMOR_POINTS[tier][pieceIndex(armorType)];
        float toughness = TOUGHNESS_BY_TIER[tier];
        float knockbackResistance = KNOCKBACK_RESISTANCE_BY_TIER[tier];

        EquipmentSlotGroup slotGroup = EquipmentSlotGroup.bySlot(armorType.getSlot());
        Identifier modifierId = Identifier.withDefaultNamespace("lyfe_armor." + armorType.getName());

        ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder();
        builder.add(Attributes.ARMOR, new AttributeModifier(modifierId, defense, AttributeModifier.Operation.ADD_VALUE), slotGroup);
        builder.add(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(modifierId, toughness, AttributeModifier.Operation.ADD_VALUE), slotGroup);
        if (knockbackResistance > 0.0f) {
            builder.add(Attributes.KNOCKBACK_RESISTANCE, new AttributeModifier(modifierId, knockbackResistance, AttributeModifier.Operation.ADD_VALUE), slotGroup);
        }
        return builder.build();
    }

    private static int pieceIndex(ArmorType armorType) {
        return switch (armorType) {
            case BOOTS -> 0;
            case LEGGINGS -> 1;
            case CHESTPLATE -> 2;
            case HELMET -> 3;
            default -> throw new IllegalArgumentException("Not a humanoid armor slot: " + armorType);
        };
    }
}
