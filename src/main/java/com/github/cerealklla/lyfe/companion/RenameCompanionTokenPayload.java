package com.github.cerealklla.lyfe.companion;

import io.netty.buffer.ByteBuf;

import com.github.cerealklla.lyfe.LyfeMod;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client-to-server: "Rename" clicked on {@code client.CompanionTokenScreen} -- real user request,
 * 2026-11-01 ("I need to be able to rename these people"). Direct mirror of {@code
 * summoning.RenameAllayBottlePayload}; re-resolves the token from whichever hand the player is
 * actually holding it in server-side, same as {@link CompanionTokenMenu} itself -- never trusts an
 * entity id from the client for this.
 */
public record RenameCompanionTokenPayload(String name) implements CustomPacketPayload {

    public static final Type<RenameCompanionTokenPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(LyfeMod.MODID, "rename_companion_token"));

    public static final StreamCodec<ByteBuf, RenameCompanionTokenPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, RenameCompanionTokenPayload::name,
            RenameCompanionTokenPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
