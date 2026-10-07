package com.github.cerealklla.lyfe.rest;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.github.cerealklla.lyfe.registration.ModMobEffects;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;
import net.neoforged.neoforge.event.level.SleepFinishedTimeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Replaces vanilla's "everyone in bed -> night instantly skips" mechanic (design doc, 2026-10-03
 * user request) with Well Rested, scaled to how long the player actually lay there.
 *
 * <p>Confirmed against the real decompiled {@code ServerLevel#tick} source before building this:
 * once enough players are (deep-)sleeping, vanilla fires {@code SleepFinishedTimeEvent} (via
 * {@code EventHooks#onSleepFinished}) to compute the clock adjustment it's about to apply, and
 * skips applying it entirely if that event is canceled -- so canceling it here is a real, complete
 * fix for the clock jump. This does NOT stop players from eventually waking on their own, nor the
 * weather-reset that rides along with the same "enough players sleeping" check -- neither was part
 * of this request.
 *
 * <p><b>A second, separate vanilla behavior had to be worked around too</b> (found via a 2026-10-03
 * playtest: "faded to black, then instantly woke up, wasn't in bed long enough to gain the buff"):
 * the exact same "enough players (deep-)sleeping" check in {@code ServerLevel#tick} ALSO
 * unconditionally calls {@code wakeUpAllPlayers()} right next to the clock-adjustment code,
 * regardless of whether that adjustment was canceled -- so every player gets force-ejected from bed
 * the moment they cross vanilla's own ~5-second deep-sleep threshold (trivially met in single-player,
 * where "100% of active players" is just the one player), not after any amount of time this mod's
 * own 5-90 second window would recognize. There's no event that lets a mod prevent that specific
 * call (confirmed -- {@code CanContinueSleepingEvent} governs a different, unrelated "is this still
 * a valid place/time to sleep" check), and this project avoids mixins. The workaround: {@code
 * Player#stopSleepInBed(forcefulWakeUp, updateLevelList)} fires an (uncancelable) {@code
 * PlayerWakeUpEvent} carrying those same two flags -- confirmed against the decompiled source that
 * vanilla's own forced global wake-up is the *only* call site using {@code (false, false)} (a
 * player's own "get up" interaction uses {@code (false, true)}; disconnecting uses
 * {@code (true, false)}; nothing else collides with this exact pair) -- so that signature uniquely
 * identifies this specific forced wake. When it fires and the player's own tracked rest session is
 * still under the 90-second cap, their bed position (still readable at this point -- the event fires
 * before {@code super.stopSleeping()} clears it) is queued and re-slept into on the very next tick,
 * via {@code ServerPlayer#startSleepInBed} (the real, validated entry point -- re-sleeping can still
 * legitimately fail, e.g. a monster wandered close in the interim, in which case the player is simply
 * left awake, same as if they'd gotten up normally). <b>Known, accepted visual cost:</b> each
 * resume briefly re-triggers the sleep screen-fade (since {@code startSleepInBed} resets vanilla's
 * own fade counter), so a long rest looks like several short naps back-to-back rather than one
 * unbroken one -- there's no mixin-free way to avoid this while still working within vanilla's
 * per-player fade-counter system; flagged for the user's own live judgment on whether it's tolerable.
 *
 * <p><b>Sitting on a {@link StoolBlock} counts as the same kind of rest session</b> (added the same
 * day, user request) -- {@link #onPlayerTick} treats {@code getVehicle() instanceof SeatEntity}
 * exactly like {@code isSleeping()}, so the elapsed-time-to-Well-Rested-duration math is shared and
 * a stool needs none of the bed-specific forced-wake workaround above (there's no vanilla "enough
 * players sitting" mechanic to fight).
 *
 * <p><b>Well Rested is now refreshed live, once a second, for as long as the rest session
 * continues</b> (corrected same day -- a playtest report, "it's not extending the well rested buff
 * as I sit here," made clear the original one-shot-on-standing-up design read as broken, since
 * nothing visible happened until you actually got up). {@link #onPlayerTick} re-applies the effect
 * with the currently-earned duration every {@link #REFRESH_INTERVAL_TICKS} ticks while resting, so
 * the buff appears (and its remaining duration visibly grows) the moment the 5-second floor is
 * crossed, not only after standing up. Standing up still applies one final precise update for
 * whatever fraction of a second elapsed since the last periodic refresh.
 *
 * <p><b>Each rest session EXTENDS whatever Well Rested duration already exists, rather than
 * replacing it</b> (corrected same day, per the user's explicit design intent, after a real live
 * playtest bug: "I watched the buff timer decrease for 33 seconds before it started increasing" --
 * a genuinely sitting-still-with-an-already-longer-buff-active case, not user error). Confirmed
 * explicitly by the user: there is NO overall cap on the accumulated total -- resting to 20 minutes,
 * playing a while, then resting a little more should be able to reach 30+ minutes, not reset. The
 * 3-20 minute range {@link #wellRestedDurationTicks} computes for a given {@code laySeconds} is one
 * session's own *contribution* (how much a rest of that length adds), not an absolute total --
 * {@link #grantWellRested} tracks how much of that contribution has already been applied this
 * session ({@link #appliedThisSession}) and only ever adds the newly-earned delta on top of the
 * player's current remaining duration, uncapped. {@link #appliedThisSession} is cleared whenever a
 * rest session ends, so the next one's delta-tracking starts fresh while still correctly building
 * on the real remaining total.
 */
public final class BedRestListener {

    // Once a second -- frequent enough to feel live, far cheaper than every tick.
    private static final int REFRESH_INTERVAL_TICKS = 20;

    // Session-only, same pattern as swim.SwimmerListener's wasSubmerged -- no persistence needed,
    // this only ever spans a single continuous (possibly resumed) sleep.
    private final Map<UUID, Long> sleepStartTick = new HashMap<>();
    private final Map<UUID, BlockPos> pendingResume = new HashMap<>();

    // How much of this session's own earned contribution (wellRestedDurationTicks) has already
    // been added to the player's real Well Rested duration -- see grantWellRested's own doc.
    private final Map<UUID, Integer> appliedThisSession = new HashMap<>();

    @SubscribeEvent
    public void onSleepFinishedTime(SleepFinishedTimeEvent event) {
        event.setCanceled(true);
    }

    // See the class doc's second section -- this is how we detect (and undo) vanilla's forced
    // global wake-up specifically, without a mixin.
    @SubscribeEvent
    public void onPlayerWakeUp(PlayerWakeUpEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (event.wakeImmediately() || event.updateLevel()) {
            return; // not the (false, false) forced-global-wake signature -- leave it alone
        }
        Long startTick = sleepStartTick.get(player.getUUID());
        if (startTick == null) {
            return;
        }
        long elapsedSeconds = (player.level().getGameTime() - startTick) / 20L;
        if (elapsedSeconds >= RestConstants.MAX_LAY_SECONDS) {
            return; // already hit the cap -- let this wake stand, the tick loop will grant the max
        }
        player.getSleepingPos().ifPresent(pos -> pendingResume.put(player.getUUID(), pos));
    }

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        UUID playerId = player.getUUID();

        BlockPos resumePos = pendingResume.remove(playerId);
        if (resumePos != null && !player.isSleeping()) {
            player.startSleepInBed(resumePos);
            return; // sleepStartTick is untouched -- the rest session continues seamlessly
        }

        if (isResting(player)) {
            long now = player.level().getGameTime();
            long startTick = sleepStartTick.computeIfAbsent(playerId, k -> now);
            if (now % REFRESH_INTERVAL_TICKS == 0) {
                grantWellRested(player, (now - startTick) / 20L);
            }
            return;
        }
        Long startTick = sleepStartTick.remove(playerId);
        if (startTick != null) {
            long laySeconds = (player.level().getGameTime() - startTick) / 20L;
            grantWellRested(player, laySeconds);
        }
        appliedThisSession.remove(playerId);
    }

    private static boolean isResting(ServerPlayer player) {
        return player.isSleeping() || player.getVehicle() instanceof SeatEntity;
    }

    /**
     * Extends (never replaces) the player's current Well Rested duration by whatever portion of
     * this session's own earned contribution hasn't already been applied -- see this class's own
     * doc for the full reasoning. Because the result only ever grows relative to what's already
     * there, plain {@code addEffect} (whose merge semantics only accept a longer duration) is
     * correct here without needing to force-remove the existing instance first.
     */
    private void grantWellRested(ServerPlayer player, long laySeconds) {
        int totalForSession = wellRestedDurationTicks(laySeconds);
        if (totalForSession <= 0) {
            return;
        }
        UUID playerId = player.getUUID();
        int alreadyApplied = appliedThisSession.getOrDefault(playerId, 0);
        int delta = totalForSession - alreadyApplied;
        if (delta <= 0) {
            return;
        }
        appliedThisSession.put(playerId, totalForSession);

        MobEffectInstance current = player.getEffect(ModMobEffects.WELL_RESTED);
        int currentDuration = current != null ? current.getDuration() : 0;
        // Deliberately uncapped -- confirmed with the user, see this class's own doc (resting to
        // 20 minutes, playing a while, then resting again should be able to reach 30+ minutes).
        int newDuration = currentDuration + delta;
        // amplifier 0, not ambient, particles off (2026-10-03 user request -- "I don't like the
        // bubbles floating off the character"), icon kept on so it's still visible in the HUD.
        player.addEffect(new MobEffectInstance(ModMobEffects.WELL_RESTED, newDuration, 0, false, false, true));
    }

    /** Pure -- 0 below the 5-second floor, scaling linearly from 3 to 20 minutes up to the 90-second cap. */
    public static int wellRestedDurationTicks(long laySeconds) {
        if (laySeconds < RestConstants.MIN_LAY_SECONDS) {
            return 0;
        }
        long clamped = Math.min(laySeconds, RestConstants.MAX_LAY_SECONDS);
        double progress = (clamped - RestConstants.MIN_LAY_SECONDS)
                / (double) (RestConstants.MAX_LAY_SECONDS - RestConstants.MIN_LAY_SECONDS);
        int span = RestConstants.MAX_WELL_RESTED_TICKS - RestConstants.MIN_WELL_RESTED_TICKS;
        return RestConstants.MIN_WELL_RESTED_TICKS + (int) Math.round(progress * span);
    }
}
