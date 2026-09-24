package com.github.cerealklla.lyfe.registration;

import java.util.function.Supplier;

import com.github.cerealklla.lyfe.LyfeMod;
import com.github.cerealklla.lyfe.data.PlayerSkills;
import com.github.cerealklla.lyfe.hunger.PlayerHunger;

import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModAttachments {

    private ModAttachments() {
    }

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, LyfeMod.MODID);

    // Synced (not just persisted): Player#getDestroySpeed runs on both sides, and
    // GatheringListener#onBreakSpeed needs a player's own client to know their own skill level for
    // that to feel consistent client/server (Phase 2, design doc Section 6's SpeedMultiplier).
    public static final Supplier<AttachmentType<PlayerSkills>> PLAYER_SKILLS = ATTACHMENT_TYPES.register(
            "player_skills",
            () -> AttachmentType.builder(PlayerSkills::new)
                    .serialize(PlayerSkills.CODEC)
                    .sync(ByteBufCodecs.fromCodecWithRegistries(PlayerSkills.CODEC.codec()))
                    .build()
    );

    // Synced: the custom hunger overlay (client-rendered) needs each player's own true hunger value
    // to display (design doc Section 10.0).
    public static final Supplier<AttachmentType<PlayerHunger>> PLAYER_HUNGER = ATTACHMENT_TYPES.register(
            "player_hunger",
            () -> AttachmentType.builder(PlayerHunger::new)
                    .serialize(PlayerHunger.CODEC)
                    .sync(ByteBufCodecs.fromCodecWithRegistries(PlayerHunger.CODEC.codec()))
                    .build()
    );
}
