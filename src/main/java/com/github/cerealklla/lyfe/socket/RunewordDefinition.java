package com.github.cerealklla.lyfe.socket;

/**
 * One fixed Runeword (name, category, how many runes it needs, and its real effect) -- the 60
 * entries in {@link RunewordCatalog}, authored once and baked in (not randomized). What IS
 * randomized per server is which exact {@link RuneId} sequence triggers each one -- see {@link
 * ServerRunewordStore}, which mirrors {@code craft.ServerRecipeStore}'s own "generate once per
 * server, persist, reuse" shape.
 *
 * @param runeCount 2-5 (explicit user spec: "The number of runes needed for each Runeword should
 *                  directly correlate to the strength/quality of the Runeword") -- stronger effects use more runes.
 */
public record RunewordDefinition(String name, EquipmentCategory category, int runeCount, RunewordEffect effect, String description) {
}
