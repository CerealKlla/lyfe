package com.github.cerealklla.lyfe.magician;

import com.github.cerealklla.lyfe.LyfeMod;

import net.minecraft.resources.Identifier;

/** Every spell this mod knows about (currently just one) -- kept separate from any one spell's own class since {@code magician.PlayerSpellProficiency}/{@code SpellCooldowns} are keyed generically by spell id, future-proofed for more than one spell. */
public final class SpellIds {

    public static final Identifier SPARK_OF_LIFE = Identifier.fromNamespaceAndPath(LyfeMod.MODID, "spark_of_life");

    private SpellIds() {
    }
}
