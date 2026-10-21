package com.github.cerealklla.lyfe.magician;

import io.netty.buffer.ByteBuf;

import com.github.cerealklla.lyfe.LyfeMod;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server-to-client: {@code spellId}'s Spell Proficiency progress just changed for the receiving
 * player -- mirrors {@code huntsman.QuarryKnowledgeProgressPayload} exactly (explicit user request,
 * 2026-10-21: "this should look visually like XP gains, same as the popup with Quarry Knowledge and
 * every other similar mechanic we have"). Triggers the transient Spell Proficiency HUD (see {@code
 * magician.client.ClientSpellProficiencyBarState}), independent of the rarer Magician-level-sum
 * popup {@code MagicianListener#syncLevelToProficiencySum} already sends.
 */
public record SpellProficiencyProgressPayload(Identifier spellId, int oldProgress, int newProgress) implements CustomPacketPayload {

    public static final Type<SpellProficiencyProgressPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(LyfeMod.MODID, "spell_proficiency_progress"));

    public static final StreamCodec<ByteBuf, SpellProficiencyProgressPayload> STREAM_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, SpellProficiencyProgressPayload::spellId,
            ByteBufCodecs.VAR_INT, SpellProficiencyProgressPayload::oldProgress,
            ByteBufCodecs.VAR_INT, SpellProficiencyProgressPayload::newProgress,
            SpellProficiencyProgressPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
