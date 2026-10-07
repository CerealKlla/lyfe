package com.github.cerealklla.lyfe.durability;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.github.cerealklla.lyfe.LyfeMod;
import com.github.cerealklla.lyfe.craft.EquipmentTierLadder;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Part of the Unbreakable Equipment feature (2026-10-06): a worn armor piece that reaches
 * {@link UnbreakableConstants#isAtZero} should contribute 0 armor/toughness/knockback-resistance
 * while staying equipped (no auto-unequip, no break animation/sound -- that's guaranteed separately
 * by the {@code durability.mixin.ItemStackUnbreakableMixin} mixin).
 *
 * <p>This exists specifically because the mixin's clamp defeats vanilla's own safeguard: {@code
 * LivingEntity#collectEquipmentChanges} only skips reapplying an item's attribute modifiers when
 * {@code ItemStack#isBroken()} is true (confirmed against the decompiled source), but the mixin
 * guarantees that's never true any more. Without this listener, a worn-to-zero armor piece would
 * keep its full vanilla armor/toughness value forever -- so this listener is the actual mechanism
 * for the "0 defense" half of the feature, not optional sugar on top of the mixin. It reapplies an
 * offsetting negative transient modifier every tick, same idiom as {@code
 * heartiness.HeartinessListener#applyMaxHealthBonus}/{@code swim.SwimmerListener#applySwimSpeed}.
 */
public final class UnbreakableArmorListener {

    private static final List<Holder<Attribute>> OFFSETTABLE_ATTRIBUTES =
            List.of(Attributes.ARMOR, Attributes.ARMOR_TOUGHNESS, Attributes.KNOCKBACK_RESISTANCE);

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        for (ArmorType armorType : EquipmentTierLadder.ARMOR_TYPES) {
            applyOffset(player, armorType);
        }
    }

    private void applyOffset(ServerPlayer player, ArmorType armorType) {
        EquipmentSlot slot = armorType.getSlot();
        ItemStack piece = player.getItemBySlot(slot);
        boolean atZero = !piece.isEmpty() && UnbreakableConstants.isAtZero(piece);

        Map<Holder<Attribute>, Double> totals = atZero ? sumContributions(piece, slot) : Map.of();

        for (Holder<Attribute> attribute : OFFSETTABLE_ATTRIBUTES) {
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance == null) {
                continue;
            }
            Identifier id = offsetId(armorType, attribute);
            instance.removeModifier(id);
            double amount = totals.getOrDefault(attribute, 0.0);
            if (amount != 0.0) {
                instance.addTransientModifier(new AttributeModifier(id, -amount, AttributeModifier.Operation.ADD_VALUE));
            }
        }
    }

    private static Map<Holder<Attribute>, Double> sumContributions(ItemStack piece, EquipmentSlot slot) {
        Map<Holder<Attribute>, Double> totals = new HashMap<>();
        piece.forEachModifier(slot, (attribute, modifier) ->
                totals.merge(attribute, modifier.amount(), Double::sum));
        return totals;
    }

    private static Identifier offsetId(ArmorType armorType, Holder<Attribute> attribute) {
        String attributeKey = attribute.unwrapKey().orElseThrow().identifier().getPath();
        return Identifier.fromNamespaceAndPath(
                LyfeMod.MODID, "unbreakable_armor_offset_" + armorType.getSerializedName() + "_" + attributeKey);
    }
}
