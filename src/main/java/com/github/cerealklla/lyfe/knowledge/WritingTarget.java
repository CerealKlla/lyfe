package com.github.cerealklla.lyfe.knowledge;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Describes what a sign/map writing dialog is being opened for -- a fence post (sign) or an item
 * frame (map). Network-only, never persisted, so a {@link StreamCodec} is all this needs.
 */
public record WritingTarget(Kind kind, BlockPos blockPos, int frameEntityId) {

    public enum Kind {
        SIGN, MAP
    }

    public static final StreamCodec<ByteBuf, WritingTarget> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8.map(s -> Kind.valueOf(s), Enum::name), WritingTarget::kind,
            BlockPos.STREAM_CODEC, WritingTarget::blockPos,
            ByteBufCodecs.VAR_INT, WritingTarget::frameEntityId,
            WritingTarget::new);

    public static WritingTarget sign(BlockPos pos) {
        return new WritingTarget(Kind.SIGN, pos, 0);
    }

    public static WritingTarget map(int frameEntityId) {
        return new WritingTarget(Kind.MAP, BlockPos.ZERO, frameEntityId);
    }
}
