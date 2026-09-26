package com.github.cerealklla.lyfe.registration;

import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import com.github.cerealklla.lyfe.LyfeMod;
import com.github.cerealklla.lyfe.data.PlayerSkills;
import com.github.cerealklla.lyfe.hunger.PlayerHunger;
import com.github.cerealklla.lyfe.knowledge.KnowledgeReference;
import com.github.cerealklla.lyfe.knowledge.PlayerKnowledge;

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
    //
    // copyOnDeath() added 2026-09-25 (see decisions.md) -- a real playtest-found bug, not a
    // pre-existing design choice: NeoForge attachments do NOT survive a player's death by default
    // (a brand-new Player entity is created on respawn, and only attachments that opt into
    // copyOnDeath() get their data carried over to it) -- confirmed against the decompiled
    // AttachmentType.Builder source, whose own doc for copyOnDeath() is literally "Requests that
    // this attachment be persisted when a player respawns." Without this, every skill's XP/level
    // was silently reset to zero on every death, the whole time -- likely never noticed before
    // because nothing forces you to look at your level again right after dying.
    public static final Supplier<AttachmentType<PlayerSkills>> PLAYER_SKILLS = ATTACHMENT_TYPES.register(
            "player_skills",
            () -> AttachmentType.builder(PlayerSkills::new)
                    .serialize(PlayerSkills.CODEC)
                    .copyOnDeath()
                    .sync(ByteBufCodecs.fromCodecWithRegistries(PlayerSkills.CODEC.codec()))
                    .build()
    );

    // Synced: the custom hunger overlay (client-rendered) needs each player's own true hunger value
    // to display (design doc Section 10.0). copyOnDeath() added 2026-09-25, same bug/reasoning as
    // PLAYER_SKILLS above.
    public static final Supplier<AttachmentType<PlayerHunger>> PLAYER_HUNGER = ATTACHMENT_TYPES.register(
            "player_hunger",
            () -> AttachmentType.builder(PlayerHunger::new)
                    .serialize(PlayerHunger.CODEC)
                    .copyOnDeath()
                    .sync(ByteBufCodecs.fromCodecWithRegistries(PlayerHunger.CODEC.codec()))
                    .build()
    );

    // Not synced -- nothing client-side ever reads this directly (design doc Section 9.3, see
    // PlayerKnowledge's own class doc). copyOnDeath() added 2026-09-25 -- this is the specific one
    // a playtest report actually caught (died in a town, couldn't write a sign for it afterward),
    // which led to finding the same gap on PLAYER_SKILLS/PLAYER_HUNGER above too.
    public static final Supplier<AttachmentType<PlayerKnowledge>> PLAYER_KNOWLEDGE = ATTACHMENT_TYPES.register(
            "player_knowledge",
            () -> AttachmentType.builder(PlayerKnowledge::new)
                    .serialize(PlayerKnowledge.CODEC)
                    .copyOnDeath()
                    .build()
    );

    // Attached to a placed sign's SignBlockEntity (block entities are AttachmentHolders too, same
    // as Entity -- see knowledge.SignListener). No default value is ever actually used: readers
    // always check getExistingData first, since a random vanilla sign has no attachment at all.
    // Not synced -- only server-side read logic (SignListener) ever looks at it.
    public static final Supplier<AttachmentType<KnowledgeReference>> SIGN_REFERENCE = ATTACHMENT_TYPES.register(
            "sign_reference",
            () -> AttachmentType.builder(holder -> new KnowledgeReference(0L, Set.of(), "", new UUID(0L, 0L)))
                    .serialize(KnowledgeReference.CODEC.fieldOf("data"))
                    .build()
    );
}
