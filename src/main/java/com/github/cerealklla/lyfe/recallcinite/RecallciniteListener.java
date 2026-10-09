package com.github.cerealklla.lyfe.recallcinite;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.github.cerealklla.lyfe.LyfeMod;
import com.github.cerealklla.lyfe.api.Lyfe;
import com.github.cerealklla.lyfe.registration.ModAttachments;
import com.github.cerealklla.lyfe.registration.ModItems;
import com.github.cerealklla.lyfe.skill.Skills;
import com.github.cerealklla.lyfe.structure.SettlemyntsStructureBridge;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerRespawnPositionEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Event glue for the Recallcinite Totem (design doc, 2026-10-09 user request) -- grant-on-login,
 * drop/reorder prevention, the bed-replacement respawn override, the "not yet bound" plot-entry
 * reminder, and the actual bind/recall mechanics the item's own hooks ({@link
 * RecallciniteTotemItem}) delegate into. See that class's own doc for why cooldown enforcement is a
 * persisted custom attachment ({@link RecallciniteData}) rather than vanilla's {@code ItemCooldowns}.
 */
public final class RecallciniteListener {

    // Flat XP amounts, same "placeholder, flagged tunable" convention every other skill's XP source
    // in this codebase uses.
    private static final long XP_BIND = 150;
    private static final long XP_RECALL = 100;

    private static final long TICKS_PER_MINUTE = 20L * 60L;
    private static final long BASE_COOLDOWN_TICKS = 60 * TICKS_PER_MINUTE;
    private static final long RECALLCINITE_STONE_BASE_COOLDOWN_TICKS = 40 * TICKS_PER_MINUTE;
    private static final long RECALLCINITE_STONE_TIER_STEP_TICKS = 5 * TICKS_PER_MINUTE;

    // Lightning ramp: starts at tick 100 (5s into the 10s channel), one flash every ~20 ticks at
    // first, accelerating to one every ~2 ticks by tick 200 (completion) -- computed as a smooth
    // "beat phase" function of elapsed time rather than persisted per-use state (no clean place to
    // stash a counter across onUseTick calls other than the ItemStack itself, which would need its
    // own new DataComponent for a purely visual effect -- not worth it). A flash fires whenever this
    // function's integer part advances between consecutive ticks.
    private static final int LIGHTNING_START_TICK = 100;
    private static final double LIGHTNING_WINDOW_TICKS = 100.0;
    private static final double LIGHTNING_FREQ_START = 1.0 / 20.0;
    private static final double LIGHTNING_FREQ_END = 1.0 / 2.0;

    // Soft, string-based cross-mod contract -- Lyfe has no compiled dependency on Blueprynts (only
    // an optional one on Settlemynts), same "matched by hardcoded Identifier" convention
    // location.LocationTracker's own class doc already establishes for Settlemynts-provided ids.
    private static final Identifier RECALLCINITE_STONE_ZONE_TYPE_ID = Identifier.fromNamespaceAndPath("blueprynts", "recallcinite_stone");

    private static final int PLOT_CHECK_INTERVAL_TICKS = 20;
    // Infrequent -- a bound plot's own Tier/zoning rarely changes mid-session, this is just keeping
    // the tooltip's "strength" readout from going stale, not anything latency-sensitive.
    private static final int STRENGTH_REFRESH_INTERVAL_TICKS = 100;

    // The totem's pinned hotbar slot -- index 8 is the RIGHTMOST hotbar slot (items list indices
    // 0-8 are the hotbar left-to-right, so 0 would be leftmost/key "1"; the user's own "slot 0"
    // means the opposite end, key "9"/rightmost -- corrected 2026-10-09 after a direct report, do
    // not change back to 0 without re-confirming).
    private static final int PINNED_SLOT = 8;

    // Session-only: whether this player was inside *some* Settlemynts plot on the last check, so the
    // "bind your totem" reminder only fires on a fresh entry, not every check while standing still
    // inside one.
    private static final Map<UUID, Boolean> wasInsidePlot = new ConcurrentHashMap<>();

    // --- Grant / drop prevention / hotbar pinning -------------------------------------------------

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        grantTotemIfMissing(player);
    }

    private void grantTotemIfMissing(ServerPlayer player) {
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).getItem() instanceof RecallciniteTotemItem) {
                return;
            }
        }
        ItemStack totem = new ItemStack(ModItems.RECALLCINITE_TOTEM.get());
        ItemStack existingPinnedSlot = inventory.getItem(PINNED_SLOT);
        if (!existingPinnedSlot.isEmpty()) {
            if (!inventory.add(existingPinnedSlot.copy())) {
                player.drop(existingPinnedSlot.copy(), false);
            }
        }
        inventory.setItem(PINNED_SLOT, totem);
    }

    @SubscribeEvent
    public void onItemToss(ItemTossEvent event) {
        if (!(event.getEntity().getItem().getItem() instanceof RecallciniteTotemItem)) {
            return;
        }
        ItemStack dropped = event.getEntity().getItem().copy();
        event.setCanceled(true);
        if (event.getPlayer() instanceof ServerPlayer serverPlayer) {
            if (!serverPlayer.getInventory().add(dropped)) {
                serverPlayer.getInventory().setItem(PINNED_SLOT, dropped);
            }
        }
    }

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        snapToPinnedSlot(player);

        if (player.tickCount % PLOT_CHECK_INTERVAL_TICKS == 0) {
            checkPlotEntryReminder(player);
        }
        if (player.tickCount % STRENGTH_REFRESH_INTERVAL_TICKS == 0) {
            refreshBoundPlotTier(player);
        }
    }

    /** Snaps a Recallcinite Totem found anywhere else in the player's own inventory back to {@link #PINNED_SLOT} every tick -- covers accidental reordering; see the class doc for the real limitation (a container quick-move can't be caught this way). */
    private void snapToPinnedSlot(ServerPlayer player) {
        var inventory = player.getInventory();
        int totemIndex = -1;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).getItem() instanceof RecallciniteTotemItem) {
                if (totemIndex == -1) {
                    totemIndex = i;
                } else {
                    // A duplicate somehow exists (e.g. a stale world-dropped copy picked back up
                    // before this listener's own ItemTossEvent guard existed) -- discard the extra
                    // rather than let two totems exist for one player.
                    inventory.setItem(i, ItemStack.EMPTY);
                }
            }
        }
        if (totemIndex != -1 && totemIndex != PINNED_SLOT) {
            ItemStack totem = inventory.getItem(totemIndex);
            ItemStack pinnedSlotItem = inventory.getItem(PINNED_SLOT);
            inventory.setItem(PINNED_SLOT, totem);
            inventory.setItem(totemIndex, pinnedSlotItem);
        }
    }

    private void checkPlotEntryReminder(ServerPlayer player) {
        if (!SettlemyntsStructureBridge.isAvailable()) {
            return;
        }
        RecallciniteData data = player.getData(ModAttachments.RECALLCINITE_DATA);
        if (data.boundLocation().isPresent()) {
            wasInsidePlot.remove(player.getUUID());
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        boolean insideNow = SettlemyntsStructureBridge.findPlotAt(level, player.blockPosition()).isPresent();
        boolean wasInside = wasInsidePlot.getOrDefault(player.getUUID(), false);
        if (insideNow && !wasInside) {
            player.sendSystemMessage(
                    Component.literal("You should bind your Recallcinite Totem here.").withStyle(ChatFormatting.LIGHT_PURPLE),
                    true);
        }
        wasInsidePlot.put(player.getUUID(), insideNow);
    }

    /** Keeps {@link RecallciniteData#boundPlotTier} current for the tooltip's "strength" readout -- see that field's own doc. No-op (and no sync) if nothing actually changed. */
    private void refreshBoundPlotTier(ServerPlayer player) {
        RecallciniteData data = player.getData(ModAttachments.RECALLCINITE_DATA);
        Optional<GlobalPos> bound = data.boundLocation();
        if (bound.isEmpty()) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        ServerLevel level = server != null ? server.getLevel(bound.get().dimension()) : null;
        if (level == null) {
            return;
        }
        int tier = recallciniteStonePlotTier(level, bound.get().pos());
        if (tier != data.boundPlotTier()) {
            data.setBoundPlotTier(tier);
            player.syncData(ModAttachments.RECALLCINITE_DATA);
        }
    }

    // --- Respawn override (replaces bed-based spawn) -----------------------------------------------

    @SubscribeEvent
    public void onRespawnPosition(PlayerRespawnPositionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        RecallciniteData data = player.getData(ModAttachments.RECALLCINITE_DATA);
        data.boundLocation().ifPresent(bound -> {
            MinecraftServer server = player.level().getServer();
            if (server == null) {
                return;
            }
            ServerLevel targetLevel = server.getLevel(bound.dimension());
            if (targetLevel == null) {
                return;
            }
            BlockPos pos = bound.pos();
            net.minecraft.world.level.portal.TeleportTransition current = event.getTeleportTransition();
            event.setTeleportTransition(new net.minecraft.world.level.portal.TeleportTransition(
                    targetLevel,
                    new net.minecraft.world.phys.Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5),
                    current.deltaMovement(), current.yRot(), current.xRot(), current.relatives(), current.postTeleportTransition()));
        });
    }

    // --- Channel lifecycle (called from RecallciniteTotemItem's own hooks) -------------------------

    /**
     * Defensive only -- in normal play this is never reached while unbound at all, since {@link
     * RecallciniteTotemItem#use} now never starts a hold session in the first place when unbound
     * (it handles that tap synchronously and immediately instead, see {@link #attemptBind}'s own
     * doc for the real story of why: a mid-hold cutoff here used to call {@code
     * player.stopUsingItem()}, which worked, but vanilla immediately restarts a new use cycle if the
     * mouse is still held when one ends -- same behavior as holding right-click through a stack of
     * food -- so the player was stuck repeatedly slow-walking the entire time they held the button
     * regardless. Kept as a no-op-visuals guard only, not a cutoff, in case some other path ever
     * starts a channel while unbound.
     */
    public static void onChannelTick(ServerPlayer player, int elapsed, int duration) {
        RecallciniteData data = player.getData(ModAttachments.RECALLCINITE_DATA);
        // Also skipped while on cooldown -- that session's duration is already shortened (see
        // RecallciniteTotemItem#getUseDuration), so letting the progress bar run here would just be
        // a brief, misleading flash to 100% rather than anything meaningful.
        if (data.boundLocation().isEmpty() || data.onCooldown(player.level().getGameTime())) {
            return;
        }
        float fraction = (float) elapsed / duration;
        PacketDistributor.sendToPlayer(player, new RecallciniteChannelProgressPayload(fraction));

        if (elapsed < LIGHTNING_START_TICK || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        double t = elapsed - LIGHTNING_START_TICK;
        double tPrev = t - 1;
        double phase = lightningPhase(t);
        double phasePrev = tPrev >= 0 ? lightningPhase(tPrev) : 0.0;
        if (Math.floor(phase) > Math.floor(phasePrev)) {
            spawnVisualLightning(level, player);
        }
    }

    private static double lightningPhase(double t) {
        // Integral of a linearly-increasing frequency from LIGHTNING_FREQ_START to
        // LIGHTNING_FREQ_END over the window -- see the class doc above.
        double slope = (LIGHTNING_FREQ_END - LIGHTNING_FREQ_START) / LIGHTNING_WINDOW_TICKS;
        return LIGHTNING_FREQ_START * t + 0.5 * slope * t * t;
    }

    private static void spawnVisualLightning(ServerLevel level, ServerPlayer player) {
        double angle = level.getRandom().nextDouble() * Math.PI * 2;
        double radius = 1.0 + level.getRandom().nextDouble() * 2.0;
        double x = player.getX() + Math.cos(angle) * radius;
        double z = player.getZ() + Math.sin(angle) * radius;
        LightningBolt bolt = new LightningBolt(EntityType.LIGHTNING_BOLT, level);
        bolt.setVisualOnly(true);
        bolt.setPos(x, player.getY(), z);
        level.addFreshEntity(bolt);
    }

    public static void onChannelReleased(ServerPlayer player, int elapsed) {
        if (elapsed <= RecallciniteTotemItem.TAP_THRESHOLD_TICKS) {
            attemptBind(player);
        } else {
            player.sendSystemMessage(Component.literal("Recall channel interrupted.").withStyle(ChatFormatting.YELLOW), true);
        }
    }

    /**
     * Opens the bind-confirm screen, or sends the "only within a plot" message -- shared by the
     * quick-tap-while-bound path above (via the normal hold/release channel, since a hold is also
     * meaningful once bound -- it might be a real recall attempt) and {@link
     * RecallciniteTotemItem#use} calling this directly, synchronously, for the unbound case, where
     * no hold-vs-tap ambiguity exists at all (recall can never succeed unbound, so there's nothing
     * to wait and see about). See that class's own doc for why going through the hold mechanism at
     * all while unbound caused real problems: vanilla restarts a use cycle immediately if the mouse
     * is still held when one ends (same behavior as holding right-click through a stack of food),
     * so any approach that let the cycle end while still unbound just looped forever with the player
     * stuck slow-walking the entire time they held the button -- confirmed via a real live report,
     * not guessed.
     */
    public static void attemptBind(ServerPlayer player) {
        if (!canBindHere((ServerLevel) player.level(), player.blockPosition())) {
            player.sendSystemMessage(Component.literal("You can only bind within a settlement plot.").withStyle(ChatFormatting.RED), true);
            return;
        }
        openBindConfirmation(player);
    }

    public static void onChannelCompleted(ServerPlayer player) {
        performRecall(player);
    }

    public static void openBindConfirmation(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new OpenRecallciniteBindConfirmPayload());
    }

    /**
     * Handles {@link ConfirmRecallciniteBindPayload} -- called from LyfeMod's payload registration.
     * Binding itself has no cooldown (see {@link RecallciniteData}'s own doc for why that changed) --
     * free to re-bind anywhere a Plot allows it, as often as wanted. XP is the only thing capped,
     * once per settlement, via {@link RecallciniteData#hasAwardedBindXp}.
     */
    public static void handleBindConfirmed(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        BlockPos pos = player.blockPosition();
        // Authoritative re-check, not just trusting the client reached this screen legitimately --
        // the player may have walked off the plot (or the screen's underlying payload could be sent
        // directly by a modified client) between the tap and this confirmation.
        if (!canBindHere(level, pos)) {
            player.sendSystemMessage(Component.literal("You can only bind within a settlement plot.").withStyle(ChatFormatting.RED), true);
            return;
        }

        RecallciniteData data = player.getData(ModAttachments.RECALLCINITE_DATA);
        data.bind(GlobalPos.of(level.dimension(), pos));
        data.setBoundPlotTier(recallciniteStonePlotTier(level, pos));

        UUID settlementCoreId = findBindablePlot(level, pos)
                .map(SettlemyntsStructureBridge.PlotInfo::settlementCoreId)
                .orElse(NO_SETTLEMENT_XP_BUCKET);
        if (!data.hasAwardedBindXp(settlementCoreId)) {
            data.markBindXpAwarded(settlementCoreId);
            Lyfe.addXp(player, Skills.RECALLCRAFT_ID, XP_BIND);
        }
        player.syncData(ModAttachments.RECALLCINITE_DATA);
        player.sendSystemMessage(Component.literal("Recallcinite Totem bound to this location.").withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }

    private static void performRecall(ServerPlayer player) {
        RecallciniteData data = player.getData(ModAttachments.RECALLCINITE_DATA);
        long gameTime = player.level().getGameTime();
        if (data.boundLocation().isEmpty()) {
            player.sendSystemMessage(Component.literal("Totem not yet bound.").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (data.onCooldown(gameTime)) {
            player.sendSystemMessage(Component.literal("Recallcinite Totem is still recharging.").withStyle(ChatFormatting.RED), true);
            return;
        }

        GlobalPos bound = data.boundLocation().get();
        MinecraftServer server = player.level().getServer();
        ServerLevel targetLevel = server != null ? server.getLevel(bound.dimension()) : null;
        if (targetLevel == null) {
            player.sendSystemMessage(Component.literal("That location no longer exists.").withStyle(ChatFormatting.RED), true);
            return;
        }

        BlockPos pos = bound.pos();
        player.teleportTo(targetLevel, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, Set.<Relative>of(), player.getYRot(), player.getXRot(), true);

        // Tier is read at the DESTINATION (the bound location), not where the player recalled from --
        // "bind at a Recallcinite Stone plot" is what earns the reduced cooldown, and since binding
        // itself is now free/uncapped (see RecallciniteData's own doc), the cooldown this reduction
        // actually gates is paid here, on recall, instead.
        int tier = recallciniteStonePlotTier(targetLevel, pos);
        data.setBoundPlotTier(tier);
        long cooldownTicks = cooldownTicksFor(player, tier);
        data.setCooldown(gameTime, cooldownTicks);

        // Re-opens bind XP eligibility at the settlement just recalled to (explicit user request --
        // see RecallciniteData#clearBindXpAwarded's own doc). The recall cooldown just set above is
        // what actually paces this, not a separate limit of its own.
        findBindablePlot(targetLevel, pos).ifPresent(plot -> data.clearBindXpAwarded(plot.settlementCoreId()));

        player.syncData(ModAttachments.RECALLCINITE_DATA);

        Lyfe.addXp(player, Skills.RECALLCRAFT_ID, XP_RECALL);
    }

    // --- Shared helpers (also used by reincarnation.ReincarnationListener's own XP-bonus hook) ------

    /**
     * The Settlemynts Plot at {@code pos}, if any -- binding is only allowed on a Plot, never in
     * open wilderness or a settlement's general core/buffer area (explicit user correction,
     * 2026-10-09: "you should only be able to right click to bind to a Plot, not outside a
     * settlement"). Recall (the 10s hold) has no such restriction -- that's the whole point,
     * "recall to this location from anywhere in the world." If Settlemynts isn't loaded at all, the
     * Plot concept doesn't exist, so binding is allowed everywhere rather than permanently blocked.
     */
    private static Optional<SettlemyntsStructureBridge.PlotInfo> findBindablePlot(ServerLevel level, BlockPos pos) {
        if (!SettlemyntsStructureBridge.isAvailable()) {
            return Optional.empty();
        }
        return SettlemyntsStructureBridge.findPlotAt(level, pos);
    }

    /** True if binding is allowed at {@code pos} -- see {@link #findBindablePlot}'s own doc. */
    private static boolean canBindHere(ServerLevel level, BlockPos pos) {
        return !SettlemyntsStructureBridge.isAvailable() || findBindablePlot(level, pos).isPresent();
    }

    // No real settlement to key XP-dedup by when Settlemynts isn't loaded at all -- one single
    // shared bucket, so a Settlemynts-less world still only ever pays out bind XP once total
    // (rather than XP-farming every re-bind, matching the spirit of "once per settlement" when
    // there's no real settlement concept to count by).
    private static final UUID NO_SETTLEMENT_XP_BUCKET = new UUID(0L, 0L);

    /** 0 if not currently standing on a plot zoned Recallcinite Stone, or Settlemynts isn't loaded -- else that plot's own Tier (1-5). */
    public static int recallciniteStonePlotTier(ServerLevel level, BlockPos pos) {
        if (!SettlemyntsStructureBridge.isAvailable()) {
            return 0;
        }
        return SettlemyntsStructureBridge.findPlotAt(level, pos)
                .filter(info -> RECALLCINITE_STONE_ZONE_TYPE_ID.equals(info.zoneTypeId()))
                .map(SettlemyntsStructureBridge.PlotInfo::tier)
                .orElse(0);
    }

    /** The bind/recall cooldown length for {@code tier} (0 = not on a Recallcinite Stone plot), Recallcraft-level-reduced. */
    public static long cooldownTicksFor(ServerPlayer player, int tier) {
        long base = tier > 0
                ? RECALLCINITE_STONE_BASE_COOLDOWN_TICKS - (tier - 1) * RECALLCINITE_STONE_TIER_STEP_TICKS
                : BASE_COOLDOWN_TICKS;
        double reduction = com.github.cerealklla.lyfe.recallcinite.RecallcraftPerks.cooldownReductionFraction(Lyfe.getLevel(player, Skills.RECALLCRAFT_ID));
        return Math.round(base * (1.0 - reduction));
    }

    /** Reincarnation XP bonus multiplier (1.0 = no bonus) for a player currently bound to a Recallcinite Stone plot -- read live at the moment of grant, see reincarnation.ReincarnationListener. */
    public static double reincarnationXpBonusMultiplier(ServerPlayer player) {
        RecallciniteData data = player.getData(ModAttachments.RECALLCINITE_DATA);
        if (data.boundLocation().isEmpty()) {
            return 1.0;
        }
        GlobalPos bound = data.boundLocation().get();
        MinecraftServer server = player.level().getServer();
        ServerLevel level = server != null ? server.getLevel(bound.dimension()) : null;
        if (level == null) {
            return 1.0;
        }
        int tier = recallciniteStonePlotTier(level, bound.pos());
        return tier > 0 ? 1.0 + 0.05 * tier : 1.0;
    }
}
