package com.github.cerealklla.lyfe.api;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import com.github.cerealklla.lyfe.LyfeMod;
import com.github.cerealklla.lyfe.knowledge.KnowledgeFactor;
import com.github.cerealklla.lyfe.map.QuestWaypointPayload;
import com.github.cerealklla.lyfe.mana.ManaNameplate;
import com.github.cerealklla.lyfe.merchant.MerchantListener;
import com.github.cerealklla.lyfe.registration.ModAttachments;
import com.github.cerealklla.lyfe.registration.ModAttributes;
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
     * The flat Shop-value bonus {@code stack} earns from its empty/filled sockets (explicit user
     * spec, 2026-10-17: "the presence of empty sockets... increases the value baseline... 1 -> 5
     * sockets, value should go from base + 20 -> base + 200") -- {@code 0} for an unsocketed item.
     * For Settlemynts/Yconomics pricing to add on top of whatever base price it already derives.
     */
    public static int getSocketValueBonus(net.minecraft.world.item.ItemStack stack) {
        com.github.cerealklla.lyfe.socket.SocketData data = stack.getOrDefault(
                com.github.cerealklla.lyfe.registration.ModItems.SOCKET_DATA.get(), com.github.cerealklla.lyfe.socket.SocketData.EMPTY);
        return com.github.cerealklla.lyfe.socket.SocketData.valueBonusForSocketCount(data.socketCount());
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

    /**
     * Sets the player's quest-origin waypoint (added 2026-10-10 for Myssions' design doc item 2) --
     * shows as an arrow on the minimap and a vague circle on the full Map screen (see {@code
     * map.ClientQuestWaypointState}'s own doc). {@code radiusBlocks} is the vagueness circle's own
     * size, entirely the caller's responsibility to compute (e.g. from {@link #getKnownFactorCount}).
     * Always private to the one targeted player.
     */
    public static void setQuestWaypoint(ServerPlayer player, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
                                         net.minecraft.core.BlockPos pos, double radiusBlocks) {
        PacketDistributor.sendToPlayer(player, new QuestWaypointPayload(Optional.of(pos), dimension.identifier(), radiusBlocks));
    }

    public static void clearQuestWaypoint(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new QuestWaypointPayload(Optional.empty(),
                Identifier.fromNamespaceAndPath("minecraft", "overworld"), 0));
    }

    /**
     * Directly grants {@code factors} toward the player's {@code knowledge.PlayerKnowledge} of
     * {@code placeId} -- added 2026-10-10 alongside the quest waypoint system, for a quest to be
     * able to simulate "someone described the place to you" (DIRECTION/DISTANCE) without the player
     * needing to have physically visited anywhere yet. {@code placeId} doesn't need to correspond to
     * a real Cartographyr entity -- {@code PlayerKnowledge} is just keyed by a stable long, nothing
     * validates it against Cartographyr's own registry.
     */
    public static void grantKnowledgeFactors(ServerPlayer player, long placeId, Set<KnowledgeFactor> factors) {
        player.getData(ModAttachments.PLAYER_KNOWLEDGE).learnLocationFactors(placeId, factors);
    }

    /** How many of the 3 {@link KnowledgeFactor}s the player has for {@code placeId} (0 if no entry at all). */
    public static int getKnownFactorCount(ServerPlayer player, long placeId) {
        return player.getData(ModAttachments.PLAYER_KNOWLEDGE).get(placeId)
                .map(entry -> entry.locationFactors().size())
                .orElse(0);
    }

    /**
     * Every settlement {@code player} has personal knowledge of (visited, or partially known via
     * {@link KnowledgeFactor}s) within {@code radiusBlocks} of {@code origin} -- added 2026-10-13 for
     * Settlemynts' Establish Highway quest, explicit user spec: "every settlement the Mayor knows
     * about (including partially known settlements)." Real logic lives in {@code
     * map.KnownSettlementsQuery} (already hard-dependent on Cartographyr, same as {@code
     * map.MapDataListener}) -- this method is just the {@code ModList}-guarded entry point, empty if
     * Cartographyr isn't loaded.
     */
    public static java.util.List<com.github.cerealklla.lyfe.map.KnownSettlementsQuery.Result> getKnownSettlementsNear(
            ServerPlayer player, net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos origin, double radiusBlocks) {
        if (!ModList.get().isLoaded("cartographyr")) {
            return java.util.List.of();
        }
        return com.github.cerealklla.lyfe.map.KnownSettlementsQuery.find(player, level, origin, radiusBlocks);
    }

    /**
     * The actual {@link KnowledgeFactor} set the player has for {@code placeId} (empty set if no
     * entry at all) -- added 2026-10-11 for Myssions' road-connection quest, which needs to copy a
     * specific OTHER player's (the posting Mayor's) real knowledge of the destination settlement
     * onto whoever accepts the quest, not just a count. Only works while that other player is
     * online (a real, accepted simplification -- no offline-attachment-read path exists yet); the
     * caller falls back to a flat grant when they aren't.
     */
    public static Set<KnowledgeFactor> getKnownFactors(ServerPlayer player, long placeId) {
        return player.getData(ModAttachments.PLAYER_KNOWLEDGE).get(placeId)
                .map(entry -> (Set<KnowledgeFactor>) java.util.EnumSet.copyOf(entry.locationFactors()))
                .orElse(java.util.EnumSet.noneOf(KnowledgeFactor.class));
    }

    /**
     * Grants {@code factors} toward {@code placeId} (same union-merge {@link #grantKnowledgeFactors}
     * already does) plus records *where this hint came from* -- added 2026-10-12, the real missing
     * half a caller needs for {@link #resolveLocationText} to be able to say "to the northeast, 2
     * days' journey from Bramton" instead of a bare, unanchored "somewhere." {@code referencePos}
     * is wherever the hint was given from (e.g. the issuing NPC's plot), not the target itself.
     * {@code grantsName} separately marks the place's real name as known, independent of direction/
     * distance (an NPC might say "there's trouble near Bramton" -- naming the place with no bearing
     * at all, or vice versa).
     */
    public static void grantPartialLocationKnowledge(ServerPlayer player, long placeId,
                                                       net.minecraft.core.BlockPos referencePos, String referenceDimensionId, String referenceDisplayName,
                                                       boolean grantsName, boolean grantsDirection, boolean grantsDistance) {
        var pk = player.getData(ModAttachments.PLAYER_KNOWLEDGE);
        java.util.Set<KnowledgeFactor> factors = java.util.EnumSet.noneOf(KnowledgeFactor.class);
        if (grantsDirection) {
            factors.add(KnowledgeFactor.DIRECTION);
        }
        if (grantsDistance) {
            factors.add(KnowledgeFactor.DISTANCE);
        }
        pk.learnLocationFactorsWithReference(placeId, factors, referencePos, referenceDimensionId, referenceDisplayName);
        if (grantsName) {
            pk.markNamed(placeId);
        }
    }

    /**
     * The single, canonical "what should this player see for {@code placeId} right now" text --
     * added 2026-10-12 to replace equivalent logic that used to live duplicated in Myssions (its own
     * {@code QuestWaypointTicker} re-implementing the same "is this upgraded yet" decision {@code
     * api.Lyfe} already had the real inputs for). {@code realName}/{@code targetPos} are the
     * caller's own already-known facts about the place itself (not persisted here -- only *how much
     * of them to reveal* is this mod's call). See {@code knowledge.PlayerKnowledge#resolveDisplayText}
     * for the actual phrasing algorithm.
     */
    public static String resolveLocationText(ServerPlayer player, long placeId, String realName, net.minecraft.core.BlockPos targetPos) {
        return player.getData(ModAttachments.PLAYER_KNOWLEDGE).resolveDisplayText(placeId, realName, targetPos);
    }

    /**
     * Every player credited toward killing {@code deadMob} right now, via the General "assist
     * marker" system (explicit user spec, 2026-10-16 -- see {@code combat.AssistTracker}'s own doc):
     * every player who dealt real damage to this mob within the last 15 seconds, not just whoever
     * landed the killing blow. Exposed here so Myssions' quest-kill tracking can give every assisting
     * player equal credit toward a kill-type quest milestone instead of only the one player the
     * {@code LivingDeathEvent}'s own damage source names.
     */
    public static Set<UUID> getAssistCredit(net.minecraft.world.entity.LivingEntity deadMob) {
        return com.github.cerealklla.lyfe.combat.AssistTracker.creditedPlayers(deadMob);
    }

    /** {@code entity}'s live current Mana (design doc, 2026-10-20) -- any {@code LivingEntity}, not just a player. */
    public static double getCurrentMana(net.minecraft.world.entity.LivingEntity entity) {
        return entity.getData(ModAttachments.ENTITY_MANA).getCurrentMana();
    }

    /** {@code entity}'s live max Mana -- the real {@link ModAttributes#MAX_MANA} Attribute value, grown by Magician level/Nature Staff tier/Crafting Quality/Runes. 0 if this entity type was never given the Attribute at all. */
    public static double getMaxMana(net.minecraft.world.entity.LivingEntity entity) {
        net.minecraft.world.entity.ai.attributes.AttributeInstance instance = entity.getAttribute(ModAttributes.MAX_MANA);
        return instance == null ? 0.0 : instance.getValue();
    }

    /**
     * Spends {@code amount} Mana from {@code entity} if (and only if) that much is currently
     * available, returning whether the spend succeeded -- the gate a spell-casting item should check
     * before actually casting. Handles the mandatory {@code syncData} call itself (see {@link
     * #addXp}'s own comment for why mutating the attachment object in place never auto-syncs).
     */
    public static boolean spendMana(net.minecraft.world.entity.LivingEntity entity, double amount) {
        boolean spent = entity.getData(ModAttachments.ENTITY_MANA).spend(amount);
        if (spent) {
            entity.syncData(ModAttachments.ENTITY_MANA);
        }
        return spent;
    }

    /**
     * A short nameplate-ready Mana indicator line for {@code entity} (design doc, 2026-10-20: "...
     * above NPC heads when not at 100%") -- empty whenever this entity type has no Mana Attribute at
     * all, or is already at full Mana. See {@code mana.ManaNameplate}'s own doc for why this is a
     * plain colored-text bar (mirroring Settlemynts' existing health-bar nameplate technique) rather
     * than a new icon renderer.
     */
    public static Optional<net.minecraft.network.chat.Component> manaNameplateLine(net.minecraft.world.entity.LivingEntity entity) {
        return ManaNameplate.lineFor(entity);
    }

    /**
     * Is {@code stack} a Nature Staff, any tier (design doc Section 24) -- a read-only predicate so
     * Settlemynts' Guard AI (an NPC spell caster, "very important" per explicit user spec) can decide
     * whether to swap in spell-casting behavior without referencing Lyfe's concrete {@code
     * magician.NatureStaffItem} class directly, same facade discipline as every other cross-mod call
     * site in this suite.
     */
    public static boolean isNatureStaff(net.minecraft.world.item.ItemStack stack) {
        return stack.getItem() instanceof com.github.cerealklla.lyfe.magician.NatureStaffItem;
    }

    /**
     * Attempts to cast Spark of Life from {@code caster} (any {@code LivingEntity} -- a player or,
     * per the explicit "very important" NPC-casting requirement, an AI-controlled mob). Returns
     * whether it actually fired (false on insufficient mana or still on cooldown) -- the caller
     * (an NPC AI goal) falls back to melee either way, so it doesn't need the finer-grained reason a
     * player's own chat feedback does.
     */
    public static boolean attemptCastSparkOfLife(net.minecraft.world.entity.LivingEntity caster) {
        return com.github.cerealklla.lyfe.magician.SparkOfLifeCasting.attemptCast(caster)
                == com.github.cerealklla.lyfe.magician.SparkOfLifeCasting.CastResult.SUCCESS;
    }

    /** Whether {@code caster} could cast Spark of Life right now without actually casting it -- lets an NPC AI goal decide "heal/ranged vs. melee fallback" without a wasted cast attempt. */
    public static boolean canCastSparkOfLife(net.minecraft.world.entity.LivingEntity caster) {
        if (!(caster.level() instanceof net.minecraft.world.level.Level level)) {
            return false;
        }
        long now = level.getGameTime();
        if (caster.getData(ModAttachments.SPELL_COOLDOWNS).isOnCooldown(com.github.cerealklla.lyfe.magician.SpellIds.SPARK_OF_LIFE, now)) {
            return false;
        }
        int proficiencyLevel = com.github.cerealklla.lyfe.magician.SparkOfLifeCasting.proficiencyLevel(caster);
        int manaCost = com.github.cerealklla.lyfe.magician.SpellProficiencyCurve.manaCost(proficiencyLevel);
        return getCurrentMana(caster) >= manaCost;
    }
}
