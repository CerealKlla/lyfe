package com.github.cerealklla.lyfe.craft;

import io.netty.buffer.ByteBuf;

import com.github.cerealklla.lyfe.LyfeMod;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client-to-server: sent once when the Crafting Guide tab is first shown, asking the server for
 * every known equipment recipe's real ingredient cost ({@link CraftingRecipesPayload}) -- same
 * zero-field shape as {@code socket.RequestRunewordRollsPayload}. The Crafting Guide only ever
 * needs ids/tiers/learned-state (already client-safe, pure static data plus the synced {@code
 * PlayerResearch} attachment) until it also wants to show a recipe's ingredient-cost tooltip
 * (2026-10-17) -- {@link ServerRecipeStore} itself is server-only (generated per-server, never
 * synced data), so that one extra piece needs a real round trip.
 */
public record RequestCraftingRecipesPayload() implements CustomPacketPayload {

    public static final Type<RequestCraftingRecipesPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(LyfeMod.MODID, "request_crafting_recipes"));

    public static final StreamCodec<ByteBuf, RequestCraftingRecipesPayload> STREAM_CODEC =
            StreamCodec.unit(new RequestCraftingRecipesPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
