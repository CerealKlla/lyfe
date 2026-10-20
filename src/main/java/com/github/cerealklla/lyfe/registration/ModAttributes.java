package com.github.cerealklla.lyfe.registration;

import com.github.cerealklla.lyfe.LyfeMod;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Four new custom (non-vanilla) {@link Attribute}s backing the Mana feature (design doc, 2026-10-20
 * user spec) -- {@code Mana}/{@code Magic Find}/{@code Gold Find} have no vanilla equivalent at all,
 * but registering them as real {@code Attribute}s (rather than inventing a parallel stat system)
 * means {@code socket.RuneCatalog}/{@code socket.SocketApplier} need zero structural changes to grant
 * them from a Rune, and any gear can bonus them the same way a vanilla sword bonuses Attack Damage --
 * via a plain {@code DataComponents.ATTRIBUTE_MODIFIERS} component.
 *
 * <p>{@code MAX_MANA}/{@code MANA_REGEN} are flat values (a resting player/NPC: 50 max mana, 0
 * regen/second, per spec). {@code MAGIC_FIND}/{@code GOLD_FIND} are percentages, stored as a plain
 * 0-100+ number meant to be read as "+N% chance"/"+N% gold" by whatever consumes them -- they stay
 * additive {@code Attribute}s like any other (an item/skill/Rune adds a flat amount via
 * {@code ADD_VALUE}), the "percentage" meaning lives entirely in how a reader (drop-chance scaling,
 * gold-roll scaling) interprets the number, not in the Attribute's own operation.
 *
 * <p>Attached to every entity type that already has an {@code AttributeSupplier} (Player, every
 * vanilla mob, and every custom NPC any loaded mod registers one for -- Settlemynts' Guard/Farmer/
 * etc. included) via {@link EntityAttributeModificationEvent#getTypes()}, deliberately not scoped to
 * Player only or to a hardcoded list of entity types -- this needs no new cross-mod dependency at
 * all (Lyfe never references a Settlemynts class), and matches the explicit user framing ("Every
 * Player/NPC has a base maxMana") plus the stated future intent ("I could see Pillagers spawning
 * with these and wanting to heal their fellow pillagers").
 */
public final class ModAttributes {

    private ModAttributes() {
    }

    public static final DeferredRegister<Attribute> ATTRIBUTES =
            DeferredRegister.create(Registries.ATTRIBUTE, LyfeMod.MODID);

    public static final DeferredHolder<Attribute, Attribute> MAX_MANA = ATTRIBUTES.register(
            "max_mana", () -> new RangedAttribute("attribute.name.lyfe.max_mana", 50.0, 0.0, 10000.0).setSyncable(true));

    public static final DeferredHolder<Attribute, Attribute> MANA_REGEN = ATTRIBUTES.register(
            "mana_regen", () -> new RangedAttribute("attribute.name.lyfe.mana_regen", 0.0, 0.0, 1000.0).setSyncable(true));

    public static final DeferredHolder<Attribute, Attribute> MAGIC_FIND = ATTRIBUTES.register(
            "magic_find", () -> new RangedAttribute("attribute.name.lyfe.magic_find", 0.0, 0.0, 1000.0).setSyncable(true));

    public static final DeferredHolder<Attribute, Attribute> GOLD_FIND = ATTRIBUTES.register(
            "gold_find", () -> new RangedAttribute("attribute.name.lyfe.gold_find", 0.0, 0.0, 1000.0).setSyncable(true));

    /** Mod-bus listener, registered in {@code LyfeMod}'s constructor -- see class doc for why this iterates every registered type instead of a fixed list. */
    public static void onEntityAttributeModification(EntityAttributeModificationEvent event) {
        for (var entityType : event.getTypes()) {
            event.add(entityType, MAX_MANA);
            event.add(entityType, MANA_REGEN);
            event.add(entityType, MAGIC_FIND);
            event.add(entityType, GOLD_FIND);
        }
    }
}
