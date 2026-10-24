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

    /**
     * Casting Speed (added 2026-10-21, explicit user spec): "speed in seconds which must elapse
     * between spells" -- a global, cross-spell gate enforced by {@code magician.SpellCooldowns}'
     * {@code isGloballyOnCooldown}/{@code startGlobalCooldown}, separate from any one spell's own
     * per-spell cooldown ({@code SpellProficiencyCurve#cooldownTicks}). With only one spell
     * registered today it rarely matters on its own (whichever of the two cooldowns is longer always
     * wins), but is the real limiter once a second spell exists -- "if we had 2 different spells you
     * can't cast them on top of each other." Base value (1.0 second) is a flagged tunable
     * placeholder, same convention as every other untuned magnitude in this mod -- nothing reduces
     * it yet (no skill/gear/Rune bonus exists for this stat), but it's a real {@code Attribute} for
     * exactly the same reason the other four are: so one can be added later with zero structural
     * change.
     */
    public static final DeferredHolder<Attribute, Attribute> CASTING_SPEED = ATTRIBUTES.register(
            "casting_speed", () -> new RangedAttribute("attribute.name.lyfe.casting_speed", 1.0, 0.0, 60.0).setSyncable(true));

    /**
     * A bound Allay's own base Mana Regen (explicit user request, 2026-10-23, right after the real
     * "why does the Allay never seem to get mana back?" fix -- see {@code magician.NatureStaffItem}'s
     * own doc) -- every other entity's base stays the attribute's own default (0.0, requires a
     * Nature Staff/Focus/skill bonus to regen at all); an Allay regens a flat 1/second on its own,
     * on top of whatever a held Nature Staff/Focus adds.
     */
    private static final double ALLAY_BASE_MANA_REGEN = 1.0;

    /** Mod-bus listener, registered in {@code LyfeMod}'s constructor -- see class doc for why this iterates every registered type instead of a fixed list. */
    public static void onEntityAttributeModification(EntityAttributeModificationEvent event) {
        for (var entityType : event.getTypes()) {
            event.add(entityType, MAX_MANA);
            if (entityType == net.minecraft.world.entity.EntityType.ALLAY) {
                event.add(entityType, MANA_REGEN, ALLAY_BASE_MANA_REGEN);
            } else {
                event.add(entityType, MANA_REGEN);
            }
            event.add(entityType, MAGIC_FIND);
            event.add(entityType, GOLD_FIND);
            event.add(entityType, CASTING_SPEED);
        }
    }
}
