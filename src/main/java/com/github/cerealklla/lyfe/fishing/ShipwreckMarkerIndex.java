package com.github.cerealklla.lyfe.fishing;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.mojang.serialization.Codec;

import com.github.cerealklla.lyfe.LyfeMod;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Save-wide record of every Shipwreck structure instance that has already had a {@link
 * ShipwreckMarkerEntity} placed for it -- keyed by the structure's own {@code StructureStart}
 * chunk-origin position (stable per-instance identity, same convention {@code
 * zone.NaturalShopVaultIndex} already uses for an identical "don't double-place" problem).
 * Shipwrecks are Overworld-only (same assumption that mirror makes), so a plain {@link BlockPos}
 * is enough -- no per-dimension key needed.
 */
public final class ShipwreckMarkerIndex extends SavedData {

    public static final SavedDataType<ShipwreckMarkerIndex> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(LyfeMod.MODID, "shipwreck_markers"),
            ShipwreckMarkerIndex::new,
            codec());

    private final Set<BlockPos> handled;

    ShipwreckMarkerIndex() {
        this(new HashSet<>());
    }

    private ShipwreckMarkerIndex(Set<BlockPos> handled) {
        this.handled = handled;
    }

    private static Codec<ShipwreckMarkerIndex> codec() {
        return Codec.list(BlockPos.CODEC).xmap(
                list -> new ShipwreckMarkerIndex(new HashSet<>(list)),
                data -> List.copyOf(data.handled));
    }

    public static ShipwreckMarkerIndex get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public boolean isHandled(BlockPos structureStartPos) {
        return handled.contains(structureStartPos);
    }

    public void markHandled(BlockPos structureStartPos) {
        handled.add(structureStartPos);
        setDirty();
    }
}
