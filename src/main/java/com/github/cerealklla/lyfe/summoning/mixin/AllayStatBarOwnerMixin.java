package com.github.cerealklla.lyfe.summoning.mixin;

import org.spongepowered.asm.mixin.Mixin;

import com.github.cerealklla.nuclyus.statbar.StatBarOwner;

import net.minecraft.world.entity.animal.allay.Allay;

/**
 * Explicit request, 2026-10-24: "make it so Allay's have health and mana bars when appropriate."
 * {@code StatBarOwner} is a pure marker interface (no methods) -- implementing it is the only thing
 * any entity class needs to do to opt into Nuclyus's floating Health/Mana bars, same as every
 * custom NPC in this suite (Settlemynts' Guard/Resident/etc.) already does directly. A plain vanilla
 * {@code Allay} can't be given a new interface any other way (never subclassed -- see {@code
 * AllayGoalInjectionListener}'s own doc for why), so this is a pure Mixin interface injection, no
 * method bodies needed at all.
 *
 * <p>"When appropriate" needs no special-casing here -- {@code HealthStatBarProvider}'s own
 * `isApplicable` already means exactly that (not already at full health), and Mana's own
 * {@code ManaStatBarProvider} already applies generically to any entity with a real {@code
 * MAX_MANA} attribute and isn't already full, which every entity gets for free (see {@code
 * registration.ModAttributes}'s own doc -- attached to every entity type with an
 * {@code AttributeSupplier} at all, Allay included, no new wiring needed). This applies to every
 * Allay, wild or bound -- deliberately not narrowed to only a summoned one, matching how every
 * other `StatBarOwner` entity in this suite already shows its bar unconditionally whenever damaged/
 * not at full mana, not just under some extra special condition.
 */
@Mixin(Allay.class)
public abstract class AllayStatBarOwnerMixin implements StatBarOwner {
}
