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
 * nothing needs to run every tick. {@link EquipmentSlotGroup#ANY} is used for every socket modifier,
 * deliberately -- a socketed bonus should apply wherever the item actually sits (mainhand weapon,
 * any armor slot, held tool), not be scoped to one specific vanilla slot type.
 *
 * <p><b>Real bug fixed 2026-10-21 (explicit user correction): socketing was wholesale-replacing the
 * item's own crafted stat bonuses, not supplementing them</b> -- confirmed by the user directly
 * ("Socketing was always meant to be supplimental to the standard stats generated from crafting,
 * why would it wipe them out?") after investigating what making Focus socketable would do to its
 * rolled stats. This method used to build {@code ATTRIBUTE_MODIFIERS} from socket contents alone,
 * which already silently destroyed a Nature Staff's Attack Damage/Speed/Mana/Mana Regen bonus (it's
 * already sockatable today, being a {@code WEAPON}) the instant it was socketed, and would have done
 * the same to Focus and crafted armor. Fixed by always rebuilding on top of {@link
 * ModItems#BASE_ATTRIBUTE_MODIFIERS} (a craft-time-stashed copy of the item's own non-socket
 * modifiers -- see that component's own doc) instead of an empty builder, falling back to the
 * item's own registered default for a plain, never-crafted-through-this-system item (a found/looted
 * vanilla sword, say).
 *
 * <p><b>Runewords still override only the SOCKET-derived stats, not the item's own base stats</b>
 * (explicit user spec: "the individual bonuses of the runes are removed, and replaced by a much more
 * powerful benefit") -- when {@link ServerRunewordStore#match} finds an active Runeword, this skips
 * adding any Gem/Rune modifiers at all, but the item's own base modifiers are untouched either way;
 * the Runeword's benefit is its own special proc effect (see {@link RunewordEffectListener}), layered
 * on top of base stats, not a replacement for them.
 */
public final class SocketApplier {

    private SocketApplier() {
    }

    /** {@code stack}'s own non-socket attribute modifiers -- the craft-time-stashed snapshot if one exists, else this item's own registered default (covers a plain vanilla/never-crafted-here item). */
    private static ItemAttributeModifiers baseModifiersOf(ItemStack stack) {
        ItemAttributeModifiers stashed = stack.get(ModItems.BASE_ATTRIBUTE_MODIFIERS.get());
        if (stashed != null) {
            return stashed;
        }
        ItemAttributeModifiers registered = stack.getItem().components().get(DataComponents.ATTRIBUTE_MODIFIERS);
        return registered != null ? registered : ItemAttributeModifiers.EMPTY;
    }

    /** Re-derives and re-sets {@code stack}'s attribute modifiers (base stats plus its current sockets -- see class doc) and, if a Runeword is active, {@code ModItems#ACTIVE_RUNEWORD_NAME} for the client tooltip. Call this any time {@code stack}'s {@code SocketData} changes. No-op if {@code stack} isn't a socketable item at all. */
    public static void recompute(ItemStack stack) {
        EquipmentCategory category = EquipmentCategory.of(stack);
        if (category == null) {
            return;
        }
        ItemAttributeModifiers base = baseModifiersOf(stack);
        SocketData data = stack.getOrDefault(ModItems.SOCKET_DATA.get(), SocketData.EMPTY);
        if (data.sockets().isEmpty()) {
            stack.set(DataComponents.ATTRIBUTE_MODIFIERS, base);
            stack.remove(ModItems.ACTIVE_RUNEWORD_NAME.get());
            return;
        }

        RunewordDefinition activeRuneword = ServerRunewordStore.match(category, data.sockets());
        if (activeRuneword != null) {
            stack.set(ModItems.ACTIVE_RUNEWORD_NAME.get(), activeRuneword.name());
        } else {
            stack.remove(ModItems.ACTIVE_RUNEWORD_NAME.get());
        }
        ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder();
        for (ItemAttributeModifiers.Entry entry : base.modifiers()) {
            builder.add(entry.attribute(), entry.modifier(), entry.slot());
        }
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
        // (activeRuneword != null): intentionally adds no socket modifiers -- see class doc. Base
        // modifiers (already copied into the builder above) still apply either way.
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, builder.build());
    }
}
