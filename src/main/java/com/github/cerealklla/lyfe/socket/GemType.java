package com.github.cerealklla.lyfe.socket;

import java.util.Locale;

/** The 6 gem types (explicit user spec). See {@link GemCatalog} for what each one actually boosts. */
public enum GemType {
    DIAMOND, RUBY, TOPAZ, AMETHYST, COBALT, JADE;

    public String displayName() {
        String name = name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
