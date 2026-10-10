package com.github.cerealklla.lyfe.api;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import com.github.cerealklla.lyfe.LyfeMod;
import com.github.cerealklla.lyfe.merchant.MerchantListener;
import com.github.cerealklla.lyfe.registration.ModAttachments;
import com.github.cerealklla.lyfe.registration.ModMobEffects;
import com.github.cerealklla.lyfe.rest.RestConstants;
import com.github.cerealklla.lyfe.skill.SkillDefinition;
import com.github.cerealklla.lyfe.skill.SkillId;
import com.github.cerealklla.lyfe.skill.SkillRegistry;
import com.github.cerealklla.lyfe.skill.Skills;
import com.github.cerealklla.lyfe.xpbar.XpGainPayload;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
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
import net.neoforged.neoforge.network.PacketDistributor;

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

    /**
     * Adds XP for a skill and returns the new total. Amounts that would take XP below 0 are clamped.
     * Doubled while the player has Well Rested active (design doc, 2026-10-03 user request) --
     * centralized here, same reasoning as the XP bar/sync below, so every skill benefits uniformly
     * rather than each listener needing its own check.
     */
    public static long addXp(Player player, SkillId skillId, long amount) {
        if (amount > 0 && player.hasEffect(ModMobEffects.WELL_RESTED)) {
            amount *= RestConstants.WELL_RESTED_XP_MULTIPLIER;
        }
        long oldXp = getXp(player, skillId);
        long newXp = player.getData(ModAttachments.PLAYER_SKILLS).addXp(skillId, amount);
        // Mutating the attachment object in place does NOT trigger a client resync on its own --
        // NeoForge's IAttachmentHolder only syncs from setData()/removeData(), never from an
        // external caller mutating an already-fetched instance (confirmed against the decompiled
        // AttachmentHolder source, 2026-09-24). Without this, effects that depend on a synced level
        // client-side (e.g. GatheringListener's SpeedMultiplier) would silently use stale data.
        player.syncData(ModAttachments.PLAYER_SKILLS);

        // Centralized here (not per-skill-listener) so every current and future skill gets the
        // transient XP bar HUD for free -- mirrors syncData/isMaxLevel already being centralized in
        // this one method. Replaces the old per-listener chat-message announcements (2026-10-02).
        // Suppressed once already at max level, same as those announcements always were.
        if (player instanceof ServerPlayer serverPlayer && !isMaxLevel(player, skillId)) {
            PacketDistributor.sendToPlayer(serverPlayer, new XpGainPayload(skillId, oldXp, newXp));
        }
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

    /**
     * Reads a skill level for a player by UUID, whether or not they're currently online -- added
     * 2026-10-09 for the Mayor skill (a settlement's Town Hall-tier zone-type gate needs the
     * founder's own Mayor level even if they're logged off). Read-only mirror of {@link
     * #addXp(MinecraftServer, UUID, SkillId, long)}'s own offline-disk-load path -- see that method's
     * doc for why this goes through {@code PlayerDataStorage}/a scratch {@code FakePlayer} rather
     * than any simplified/cached approach. Returns 0 if the UUID has never played on this server.
     */
    public static int getLevel(MinecraftServer server, UUID playerId, SkillId skillId) {
        ServerPlayer online = server.getPlayerList().getPlayer(playerId);
        if (online != null) {
            return getLevel(online, skillId);
        }

        PlayerDataStorage playerIo = server.getPlayerList().getPlayerIo();
        Optional<CompoundTag> saved = playerIo.load(new NameAndId(playerId, "Unknown"));
        if (saved.isEmpty()) {
            return 0;
        }

        FakePlayer fake = new FakePlayer(server.overworld(), new GameProfile(playerId, "Unknown"));
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(fake.problemPath(), LyfeMod.LOGGER)) {
            ValueInput input = TagValueInput.create(reporter, server.registryAccess(), saved.get());
            fake.load(input);
        }
        return getLevel(fake, skillId);
    }

    /**
     * The Mayor skill's per-plot-upgrade XP reward and per-Zone-Type unlock level -- exposed here
     * (2026-10-09) so Settlemynts can grant/gate against them without a direct dependency on this
     * mod's internal {@code mayor} package. See {@code mayor.MayorConstants}'s own doc for the
     * reasoning behind the actual numbers (both explicitly flagged as tunable placeholders).
     */
    public static int mayorXpForPlotUpgrade(int newTier) {
        return com.github.cerealklla.lyfe.mayor.MayorConstants.xpForPlotUpgrade(newTier);
    }

    /** "Mayor should also gain xp for every 10 gold taxed" -- not called by anything yet, Settlemynts has no tax system to call it from (explicit user note); ready for whenever that exists. */
    public static int mayorXpForGoldTaxed(int goldTaxed) {
        return com.github.cerealklla.lyfe.mayor.MayorConstants.xpForGoldTaxed(goldTaxed);
    }

    public static int minMayorLevelForZoneType(Identifier zoneTypeId) {
        return com.github.cerealklla.lyfe.mayor.MayorConstants.minMayorLevelForZoneType(zoneTypeId.getPath());
    }

    /**
     * The Merchant skill's per-player buy/sell price bonus, as a fraction (0.0-0.20) -- exposed here
     * (2026-10-08) so other mods can apply the exact same formula {@code merchant.MerchantListener}
     * already uses for vanilla NPC trades to their own trading systems (Settlemynts' Settlement Shop
     * buy/sell pricing), without duplicating or re-deriving it. Safe to call even on a server without
     * Yconomics loaded -- the Merchant skill's XP/level tracking itself has no dependency on Yconomics,
     * only {@code MerchantListener}'s own trade-granting/Coin-Purse-tier logic does.
     */
    public static double getMerchantPriceBonusFraction(Player player) {
        return MerchantListener.bonusFraction(getLevel(player, Skills.MERCHANT_ID));
    }

    /**
     * The exact (unrounded) icons value baked into {@code stack} by {@code
     * cook.CookingListener#bakeIcons}, or empty if it's not a crafted-food item at all -- for
     * Settlemynts' per-quality Shop listings (2026-10-10), which need the real value rather than
     * the lossy rounded nutrition or the display-text approximation.
     */
    public static java.util.Optional<Double> getCraftedFoodIcons(net.minecraft.world.item.ItemStack stack) {
        return java.util.Optional.ofNullable(stack.get(com.github.cerealklla.lyfe.registration.ModItems.CRAFTED_FOOD_ICONS.get()));
    }

    /**
     * Whether the player has already hit {@code skillId}'s max level -- every "+N XP (Level M)"
     * chat message caller should check this first and skip the message once true, since XP earned
     * past max level still gets added (see {@code PlayerSkills#addXp}, uncapped) but no longer
     * changes anything the player can see, so announcing it is just noise. Unregistered skills
     * report {@code false} (level 0 is never max).
     */
    public static boolean isMaxLevel(Player player, SkillId skillId) {
        return getSkillDefinition(skillId)
                .map(def -> getLevel(player, skillId) >= def.xpCurve().maxLevel())
                .orElse(false);
    }

    public static Optional<SkillDefinition> getSkillDefinition(SkillId skillId) {
        return SkillRegistry.get(skillId);
    }

    /** Every registered skill visible right now — excludes skills whose required mod isn't loaded (design doc Section 8). */
    public static Collection<SkillDefinition> getAvailableSkills() {
        return SkillRegistry.available(ModList.get()::isLoaded);
    }

    /**
     * Every concrete item id a generic crafting-recipe ingredient group (e.g. "Any Log") accepts --
     * added 2026-10-09 so Settlemynts' NPC plot crafting (a plot autonomously converting raw
     * materials into finished goods, no live player/menu involved) can resolve {@code
     * craft.GeneratedRecipe#genericComponents} the same way a real player's own crafting already
     * does, without needing its own copy of this group data. Forwards to {@code
     * craft.ComponentGroups#membersOf}; an unknown group name returns an empty list.
     */
    public static java.util.List<Identifier> componentGroupMembers(String groupName) {
        return com.github.cerealklla.lyfe.craft.ComponentGroups.membersOf(groupName);
    }
}
