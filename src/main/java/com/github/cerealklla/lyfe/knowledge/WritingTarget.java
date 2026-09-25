package com.github.cerealklla.lyfe.knowledge;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Describes what a sign/map writing dialog is being opened for -- a specific placed sign
 * ({@code blockPos} meaningful) or the map currently held in the writer's main hand
 * ({@code blockPos} unused, since the target is contextual: "whatever they're holding when they
 * submit"). Network-only, never persisted, so a {@link StreamCodec} is all this needs.
 */
public record WritingTarget(Kind kind, BlockPos blockPos) {

    public enum Kind {
        SIGN, MAP
    }

    public static final StreamCodec<ByteBuf, WritingTarget> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8.map(s -> Kind.valueOf(s), Enum::name), WritingTarget::kind,
            BlockPos.STREAM_CODEC, WritingTarget::blockPos,
            WritingTarget::new);

    public static WritingTarget sign(BlockPos pos) {
        return new WritingTarget(Kind.SIGN, pos);
    }

    public static WritingTarget map() {
        return new WritingTarget(Kind.MAP, BlockPos.ZERO);
    }
}
