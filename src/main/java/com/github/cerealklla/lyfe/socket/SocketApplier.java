package com.github.cerealklla.lyfe.socket;

import java.util.List;

import com.github.cerealklla.lyfe.LyfeMod;
import com.github.cerealklla.lyfe.registration.ModItems;

import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/**
 * Rebuilds {@code DataComponents.ATTRIBUTE_MODIFIERS} on a socketed stack from its current {@link
 * SocketData} -- the one shared mechanism both Gems and Runes apply their passive bonuses through
 * (see {@link GemCatalog}/{@link RuneCatalog}'s own doc for why every stat is a real vanilla {@link
 * net.minecraft.world.entity.ai.attributes.Attribute} rather than a bespoke percentage system).
 * Baked statically onto the stack (not a live per-tick recompute) -- vanilla's own equip pipeline
 * already applies/removes a stack's {@code ATTRIBUTE_MODIFIERS} component automatically on equip/
 * unequip (confirmed against {@code durability.UnbreakableArmorListener}'s own doc, which relies on
 * this same vanilla behavior), so a full rebuild here is all that's needed whenever sockets change;
 * nothing needs to run every tick. {@link EquipmentSlotGroup#ANY} is used for every modifier,
 * deliberately -- a socketed bonus should apply wherever the item actually sits (mainhand weapon,
 * any armor slot, held tool), not be scoped to one specific vanilla slot type.
 *
 * <p><b>Runewords override entirely, not additively</b> (explicit user spec: "the individual bonuses
 * of the runes are removed, and replaced by a much more powerful benefit") -- when {@link
 * ServerRunewordStore#match} finds an active Runeword, this produces ZERO attribute modifiers at
 * all; the entire benefit is the Runeword's own special proc effect (see {@link
 * RunewordEffectListener}), not a stat line.
 */
public final class SocketApplier {

    private SocketApplier() {
    }

    /** Re-derives and re-sets {@code stack}'s attribute modifiers from its current sockets. Call this any time {@code stack}'s {@code SocketData} changes. No-op if {@code stack} isn't a socketable item at all. */
    public static void recompute(ItemStack stack) {
        EquipmentCategory category = EquipmentCategory.of(stack);
        if (category == null) {
            return;
        }
        SocketData data = stack.getOrDefault(ModItems.SOCKET_DATA.get(), SocketData.EMPTY);
        if (data.sockets().isEmpty()) {
            stack.remove(DataComponents.ATTRIBUTE_MODIFIERS);
            return;
        }

        RunewordDefinition activeRuneword = ServerRunewordStore.match(category, data.sockets());
        ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder();
        if (activeRuneword == null) {
            List<SocketSlot> sockets = data.sockets();
            for (int i = 0; i < sockets.size(); i++) {
                SocketSlot slot = sockets.get(i);
                Identifier modifierId = Identifier.fromNamespaceAndPath(LyfeMod.MODID, "socket_" + i);
                switch (slot.kind()) {
                    case GEM -> {
                        GemCatalog.StatEntry entry = GemCatalog.entryFor(slot.gemType().orElseThrow(), category);
                        double amount = entry.amountFor(slot.gemQuality().orElseThrow());
                        builder.add(entry.attribute(), new AttributeModifier(modifierId, amount, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.ANY);
                    }
                    case RUNE -> {
                        RuneCatalog.RuneStat stat = RuneCatalog.of(slot.runeId().orElseThrow());
                        builder.add(stat.attribute(), new AttributeModifier(modifierId, stat.amount(), AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.ANY);
                    }
                    case EMPTY -> {
                        // Nothing to apply.
                    }
                }
            }
        }
        // (activeRuneword != null): intentionally builds nothing -- see class doc.
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, builder.build());
    }
}
