package com.github.cerealklla.lyfe.registration;

import java.util.function.Supplier;

import com.github.cerealklla.lyfe.LyfeMod;
import com.github.cerealklla.lyfe.data.PlayerSkills;

import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModAttachments {

    private ModAttachments() {
    }

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, LyfeMod.MODID);

    public static final Supplier<AttachmentType<PlayerSkills>> PLAYER_SKILLS = ATTACHMENT_TYPES.register(
            "player_skills",
            () -> AttachmentType.builder(PlayerSkills::new).serialize(PlayerSkills.CODEC).build()
    );
}
