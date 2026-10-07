package com.github.cerealklla.lyfe.fishing;

import net.minecraft.world.item.Item;

/** A plain key matching one {@link LockedChestItem} variant by name -- no per-instance state needed. */
public class KeyItem extends Item {

    private final String variant;

    public KeyItem(String variant, Properties properties) {
        super(properties);
        this.variant = variant;
    }

    public String variant() {
        return variant;
    }
}
