package com.github.cerealklla.lyfe.magician;

import java.util.List;
import java.util.Map;

/** Every known {@link Spell}, mirrors {@link SpellIds}' "every known X" shape. */
public final class Spells {

    public static final Spell SPARK_OF_LIFE = new Spell(SpellIds.SPARK_OF_LIFE,
            List.of(SpellEffects.HEAL_ORGANIC_ALLY_LIFE, SpellEffects.DAMAGE_ENEMY_ORGANIC_LIFE));

    public static final Map<net.minecraft.resources.Identifier, Spell> ALL = Map.of(SPARK_OF_LIFE.id(), SPARK_OF_LIFE);

    private Spells() {
    }
}
