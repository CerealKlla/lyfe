package com.github.cerealklla.lyfe.xpbar;

import io.netty.buffer.ByteBuf;

import com.github.cerealklla.lyfe.LyfeMod;
import com.github.cerealklla.lyfe.skill.SkillId;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server-to-client: a skill's XP just changed for the receiving player, carrying both the before
 * and after totals so the client can animate a fill (not just snap to the new value) without
 * needing to cache a previous total itself. Triggers the transient XP bar HUD (see {@code
 * xpbar.client.ClientXpBarState}) -- replaces the old chat-message XP announcements.
 */
public record XpGainPayload(SkillId skillId, long oldXp, long newXp) implements CustomPacketPayload {

    public static final Type<XpGainPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(LyfeMod.MODID, "xp_gain"));

    public static final StreamCodec<ByteBuf, XpGainPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8.map(SkillId::new, SkillId::value), XpGainPayload::skillId,
            ByteBufCodecs.VAR_LONG, XpGainPayload::oldXp,
            ByteBufCodecs.VAR_LONG, XpGainPayload::newXp,
            XpGainPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
