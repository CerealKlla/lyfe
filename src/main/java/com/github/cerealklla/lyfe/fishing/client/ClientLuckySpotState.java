package com.github.cerealklla.lyfe.fishing.client;

import java.util.Optional;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;

/**
 * Client-only render state for the current cast's "lucky spot" (design doc Section D) -- the
 * established pattern in this mod for "visible to one player only" (per research: a real networked
 * Entity would be visible to everyone, same problem {@code rest.SeatEntity} didn't have to solve).
 * Set from {@code LuckySpotPayload}, spawns a plain vanilla particle each client tick -- purely
 * visual, no gameplay logic lives here.
 */
public final class ClientLuckySpotState {

    private static Optional<BlockPos> spot = Optional.empty();

    private ClientLuckySpotState() {
    }

    public static void set(Optional<BlockPos> pos) {
        spot = pos;
    }

    public static void tick() {
        if (spot.isEmpty()) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        BlockPos pos = spot.get();
        level.addParticle(ParticleTypes.BUBBLE_COLUMN_UP,
                pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                0.0, 0.1, 0.0);
    }
}
