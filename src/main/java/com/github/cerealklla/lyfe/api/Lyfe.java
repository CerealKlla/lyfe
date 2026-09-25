package com.github.cerealklla.lyfe.api;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import com.github.cerealklla.lyfe.LyfeMod;
import com.github.cerealklla.lyfe.registration.ModAttachments;
import com.github.cerealklla.lyfe.skill.SkillDefinition;
import com.github.cerealklla.lyfe.skill.SkillId;
import com.github.cerealklla.lyfe.skill.SkillRegistry;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.PlayerDataStorage;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * The stable public entry point for a player's skill data. Other mods (and Lyfe's own skill
 * implementations) should call these rather than touch {@code PlayerSkills}/the attachment
 * directly, so internal storage changes never require dependents to rewrite their integration —
 * same pattern as Cartographyr's {@code Cartography} facade.
 */
public final class Lyfe {

    private Lyfe() {
    }

    public static long getXp(Player player, SkillId skillId) {
        return player.getData(ModAttachments.PLAYER_SKILLS).getXp(skillId);
    }

    /** Adds XP for a skill and returns the new total. Amounts that would take XP below 0 are clamped. */
    public static long addXp(Player player, SkillId skillId, long amount) {
        long newXp = player.getData(ModAttachments.PLAYER_SKILLS).addXp(skillId, amount);
        // Mutating the attachment object in place does NOT trigger a client resync on its own --
        // NeoForge's IAttachmentHolder only syncs from setData()/removeData(), never from an
        // external caller mutating an already-fetched instance (confirmed against the decompiled
        // AttachmentHolder source, 2026-09-24). Without this, effects that depend on a synced level
        // client-side (e.g. GatheringListener's SpeedMultiplier) would silently use stale data.
        player.syncData(ModAttachments.PLAYER_SKILLS);
        return newXp;
    }

    /**
     * Adds XP for a skill to a player by UUID, whether or not they're currently online (added
     * 2026-09-25, see decisions.md -- e.g. crediting a sign/map's writer when someone else reads
     * it, even if the writer logged off ages ago). If the target is online, this is exactly {@link
     * #addXp(Player, SkillId, long)}. If offline, it loads their saved player data directly from
     * disk via {@code PlayerDataStorage} (bypassing {@code PlayerList}, whose own save/load methods
     * are connection-gated) into a scratch {@code FakePlayer} used purely as an {@code
     * AttachmentHolder} vessel -- NeoForge attachments round-trip through a player's normal saved
     * NBT under the {@code "neoforge:attachments"} key, the same codec path as the online case, so
     * this isn't a separate/simplified serialization. A no-op if the UUID has never played on this
     * server (no save file exists yet).
     *
     * <p><b>Deliberately re-checks online status first, rather than always going through disk</b> --
     * touching another player's save file directly while they're actually connected risks a lost
     * update (their own in-memory state overwriting this write on their next save) or, worse,
     * writing a `FakePlayer`'s empty position/inventory/etc. over their real data. Only ever touch
     * the disk path when confirmed offline.
     */
    public static void addXp(MinecraftServer server, UUID playerId, SkillId skillId, long amount) {
        ServerPlayer online = server.getPlayerList().getPlayer(playerId);
        if (online != null) {
            addXp(online, skillId, amount);
            return;
        }

        PlayerDataStorage playerIo = server.getPlayerList().getPlayerIo();
        Optional<CompoundTag> saved = playerIo.load(new NameAndId(playerId, "Unknown"));
        if (saved.isEmpty()) {
            return;
        }

        FakePlayer fake = new FakePlayer(server.overworld(), new GameProfile(playerId, "Unknown"));
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(fake.problemPath(), LyfeMod.LOGGER)) {
            ValueInput input = TagValueInput.create(reporter, server.registryAccess(), saved.get());
            fake.load(input);
        }
        fake.getData(ModAttachments.PLAYER_SKILLS).addXp(skillId, amount);
        playerIo.save(fake);
    }

    /** The player's current level in a skill, per that skill's own XP curve (design doc Section 3). Unregistered skills report level 0. */
    public static int getLevel(Player player, SkillId skillId) {
        long xp = getXp(player, skillId);
        return getSkillDefinition(skillId)
                .map(def -> def.xpCurve().levelForXp(xp))
                .orElse(0);
    }

    public static Optional<SkillDefinition> getSkillDefinition(SkillId skillId) {
        return SkillRegistry.get(skillId);
    }

    /** Every registered skill visible right now — excludes skills whose required mod isn't loaded (design doc Section 8). */
    public static Collection<SkillDefinition> getAvailableSkills() {
        return SkillRegistry.available(ModList.get()::isLoaded);
    }
}
