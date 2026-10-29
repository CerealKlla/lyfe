package com.github.cerealklla.lyfe.magician;

import java.util.List;

import net.minecraft.resources.Identifier;

/** A spell as a list of {@link SpellEffect}s -- see {@link Spells} for every known one. */
public record Spell(Identifier id, List<SpellEffect> effects) {
}
