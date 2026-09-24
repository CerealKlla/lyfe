package com.github.cerealklla.lyfe.gathering;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import com.github.cerealklla.lyfe.LyfeMod;

import net.minecraft.core.GlobalPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Tracks the exact positions of gathering-relevant blocks (logs/ores) that a player has placed,
 * so {@link GatheringListener} can withhold XP/bonus-yield/whole-structure-chance for them --
 * otherwise a player could chop one natural tree, place the log back down, and re-chop it forever
 * for infinite XP/wood (design doc Section 6; anti-farming decided 2026-09-24, see decisions.md).
 *
 * <p>A position is untracked the instant it's broken (placed or not -- untracking an untracked
 * position is a no-op), so this only ever holds currently-placed-but-not-yet-broken positions,
 * not an ever-growing history.
 */
public final class PlacedGatheringBlocks extends SavedData {

    public static final int SCHEMA_VERSION = 1;

    public static final SavedDataType<PlacedGatheringBlocks> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(LyfeMod.MODID, "placed_gathering_blocks"),
            PlacedGatheringBlocks::new,
            codec()
    );

    private final int schemaVersion;
    private final Set<GlobalPos> placed;

    // Package-private (not private): test-suite construction, same convention as Cartographyr's
    // CartographySavedData.
    PlacedGatheringBlocks() {
        this(SCHEMA_VERSION, new HashSet<>());
    }

    private PlacedGatheringBlocks(int schemaVersion, Set<GlobalPos> placed) {
        this.schemaVersion = schemaVersion;
        this.placed = placed;
    }

    private static Codec<PlacedGatheringBlocks> codec() {
        return RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("schema_version").forGetter(d -> d.schemaVersion),
                Codec.list(GlobalPos.CODEC).fieldOf("placed").forGetter(d -> List.copyOf(d.placed))
        ).apply(i, (schemaVersion, placedList) -> new PlacedGatheringBlocks(schemaVersion, new HashSet<>(placedList))));
    }

    // Deliberately MinecraftServer, never ServerLevel#getDataStorage() -- same reasoning as
    // Cartographyr's Cartography facade: that accessor is per-dimension in this Minecraft version,
    // not world-level, and a placed log tracked in one dimension must stay tracked if the position
    // is later referenced via a different dimension's accessor (it won't be, in practice, but using
    // the world-level store is what makes GlobalPos meaningful here at all).
    public static PlacedGatheringBlocks get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public void markPlaced(GlobalPos pos) {
        if (placed.add(pos)) {
            setDirty();
        }
    }

    public boolean isPlaced(GlobalPos pos) {
        return placed.contains(pos);
    }

    public void clearPlaced(GlobalPos pos) {
        if (placed.remove(pos)) {
            setDirty();
        }
    }

    /** Fast-path check so callers (e.g. the piston guard) can skip more expensive work when nothing is tracked at all. */
    public boolean isEmpty() {
        return placed.isEmpty();
    }
}
