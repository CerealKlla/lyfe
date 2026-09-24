package com.github.cerealklla.lyfe.skill;

import com.mojang.serialization.Codec;

/** Stable identifier for a {@link SkillDefinition}, e.g. {@code "lumberjack"}. */
public record SkillId(String value) {
    public static final Codec<SkillId> CODEC = Codec.STRING.xmap(SkillId::new, SkillId::value);
}
