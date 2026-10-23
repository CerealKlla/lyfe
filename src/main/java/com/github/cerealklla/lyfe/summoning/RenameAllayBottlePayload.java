package com.github.cerealklla.lyfe.summoning;

import io.netty.buffer.ByteBuf;

import com.github.cerealklla.lyfe.LyfeMod;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client-to-server: "Rename" clicked on {@code client.AllayBottleScreen} (design doc Section 27 --
 * "The Allay GUI will allow you to rename"). Re-resolves the bottle from whichever hand the player
 * is actually holding it in server-side, same as {@link AllayBottleMenu} itself -- never trusts an
 * entity id or slot index from the client for this.
 */
public record RenameAllayBottlePayload(String name) implements CustomPacketPayload {

    public static final Type<RenameAllayBottlePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(LyfeMod.MODID, "rename_allay_bottle"));

    public static final StreamCodec<ByteBuf, RenameAllayBottlePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, RenameAllayBottlePayload::name,
            RenameAllayBottlePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
