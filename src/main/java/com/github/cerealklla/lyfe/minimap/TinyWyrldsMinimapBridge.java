package com.github.cerealklla.lyfe.minimap;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * The one place this mod ever references {@code com.github.cerealklla.tinywyrlds.*} directly --
 * every real call site (see {@link WyrldPieceTracker}) goes through this, gated behind {@code
 * ModList.get().isLoaded("tinywyrlds")}, same optional-soft-dependency shape as {@code
 * structure.SettlemyntsStructureBridge}. Deliberately returns a plain Lyfe-owned {@link
 * PieceBounds} rather than TinyWyrlds' own {@code OneOffGenerator.CurrentPieceInfo} record --
 * nothing outside this one file needs to know that type exists.
 *
 * <p><b>The minimap's own dimension-id check does NOT go through this bridge</b> -- see {@link
 * MinimapOverlay}'s hardcoded {@code ResourceKey}, same "match another mod's content by a
 * hardcoded Identifier, no compiled dependency" precedent this file's own Settlemynts/Blueprynts
 * ghost-marker code already uses. That check runs every client frame; a real cross-mod call for a
 * pure dimension-key comparison would be unnecessary overhead and an unnecessary dependency for
 * something that's really just data. This bridge exists for the one thing that genuinely needs
 * TinyWyrlds' own logic: resolving which real saved piece a player is standing inside.
 */
public final class TinyWyrldsMinimapBridge {

    private TinyWyrldsMinimapBridge() {
    }

    /** @param min/@param maxExclusive the piece's own real-world AABB, in whatever dimension the
     *  player was actually in when this was resolved (always Wyrld Space in practice). */
    public record PieceBounds(BlockPos min, BlockPos maxExclusive) {
    }

    public static boolean isWyrldSpace(ResourceKey<Level> dimension) {
        return com.github.cerealklla.tinywyrlds.api.TinyWyrlds.isWyrldSpace(dimension);
    }

    /** @return empty if {@code player} isn't currently inside any TinyWyrlds Wyrld at all. */
    public static Optional<PieceBounds> getCurrentPiece(ServerPlayer player) {
        return com.github.cerealklla.tinywyrlds.api.TinyWyrlds.getCurrentPiece(player)
                .map(info -> new PieceBounds(info.minCorner(), info.maxCornerExclusive()));
    }
}
