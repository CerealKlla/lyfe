package com.github.cerealklla.lyfe.registration;

import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import com.github.cerealklla.lyfe.LyfeMod;
import com.github.cerealklla.lyfe.data.PlayerSkills;
import com.github.cerealklla.lyfe.heartiness.PlayerHeartiness;
import com.github.cerealklla.lyfe.hunger.PlayerHunger;
import com.github.cerealklla.lyfe.knowledge.KnowledgeReference;
import com.github.cerealklla.lyfe.knowledge.PlayerKnowledge;
import com.github.cerealklla.lyfe.recallcinite.RecallciniteData;
import com.github.cerealklla.lyfe.research.PlayerResearch;
import com.github.cerealklla.lyfe.swim.PlayerAir;

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

    // Synced: the custom air-bubble overlay (client-rendered) needs each player's own true air
    // value to display (design doc Section 5's Swimmer mechanic). copyOnDeath(), same reasoning as
    // PLAYER_HUNGER above.
    public static final Supplier<AttachmentType<PlayerAir>> PLAYER_AIR = ATTACHMENT_TYPES.register(
            "player_air",
            () -> AttachmentType.builder(PlayerAir::new)
                    .serialize(PlayerAir.CODEC)
                    .copyOnDeath()
                    .sync(ByteBufCodecs.fromCodecWithRegistries(PlayerAir.CODEC.codec()))
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

    // Not synced -- server-only logic (design doc Section 19.5), same reasoning as PLAYER_KNOWLEDGE.
    // copyOnDeath() so research progress/learned recipes survive death like every other attachment.
    public static final Supplier<AttachmentType<PlayerResearch>> PLAYER_RESEARCH = ATTACHMENT_TYPES.register(
            "player_research",
            () -> AttachmentType.builder(PlayerResearch::new)
                    .serialize(PlayerResearch.CODEC)
                    .copyOnDeath()
                    .build()
    );

    // Not synced -- purely server-side bookkeeping (design doc, 2026-10-03), same reasoning as
    // PLAYER_RESEARCH above. copyOnDeath() so a death mid-recovery doesn't lose the banked debt --
    // harmless either way since clampTo() self-corrects against real missing health on next use.
    public static final Supplier<AttachmentType<PlayerHeartiness>> PLAYER_HEARTINESS = ATTACHMENT_TYPES.register(
            "player_heartiness",
            () -> AttachmentType.builder(PlayerHeartiness::new)
                    .serialize(PlayerHeartiness.CODEC)
                    .copyOnDeath()
                    .build()
    );

    // Synced (added 2026-10-09, user request: "if this is on cooldown the tooltip should show me
    // what the current cooldown timer is") -- the totem's own tooltip reads the holding player's
    // own cooldown end-time client-side, same reasoning PLAYER_SKILLS/PLAYER_AIR already document
    // for why a client-displayed value needs a real sync, not just a one-shot message. copyOnDeath()
    // matters a lot here specifically: a bound location/cooldown must survive death (that's the
    // whole point of replacing bed-based respawn), same reasoning as every other attachment's
    // copyOnDeath() already documents.
    public static final Supplier<AttachmentType<RecallciniteData>> RECALLCINITE_DATA = ATTACHMENT_TYPES.register(
            "recallcinite_data",
            () -> AttachmentType.builder(RecallciniteData::new)
                    .serialize(RecallciniteData.CODEC)
                    .copyOnDeath()
                    .sync(ByteBufCodecs.fromCodecWithRegistries(RecallciniteData.CODEC.codec()))
                    .build()
    );
}
