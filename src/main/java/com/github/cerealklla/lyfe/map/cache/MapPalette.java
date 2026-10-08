package com.github.cerealklla.lyfe.map.cache;

import net.minecraft.world.level.material.MapColor;

/**
 * Packs a sampled block column's {@link MapColor}/{@link MapColor.Brightness} into a single {@code
 * short} for {@link TerrainCache} storage, via vanilla's own {@code MapColor#getPackedId}/{@code
 * MapColor#getColorFromPackedId} -- the exact packed byte format real vanilla map item data already
 * uses, reused here rather than reinventing an equivalent encoding. That packed id is a {@code byte}
 * (0-255, read as unsigned), which leaves no spare value for a sentinel -- a {@code short} is used
 * instead so {@link #UNSAMPLED} (-1) is available as an unambiguous "nothing recorded here yet" flag.
 */
public final class MapPalette {

    public static final short UNSAMPLED = -1;

    private MapPalette() {
    }

    public static short encode(MapColor color, MapColor.Brightness brightness) {
        return (short) (color.getPackedId(brightness) & 0xFF);
    }

    /** ARGB color for a stored index, or fully transparent for {@link #UNSAMPLED}. */
    public static int argb(short index) {
        if (index == UNSAMPLED) {
            return 0;
        }
        return MapColor.getColorFromPackedId(index);
    }
}
