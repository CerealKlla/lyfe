package com.github.cerealklla.lyfe.recallcinite.client;

import com.github.cerealklla.nuclyus.request.ClientRequestSlot;

/**
 * Client-side bridge for the Recallcinite Totem's two transient client needs -- deliberately zero
 * client-only imports, same reasoning as {@code knowledge.ClientWritingRequest}: written from the
 * common {@code LyfeMod#registerPayloads} handler (which must stay harmless to class-load on a
 * dedicated server), read/cleared from the genuinely client-only tick listener in {@code
 * LyfeModClient} that's the only place allowed to touch {@code Minecraft}/{@code Screen}.
 *
 * <p>{@code channelFraction}/{@code ticksSinceProgressUpdate} drive {@code
 * RecallciniteChannelOverlay}'s progress bar -- the bar is shown only while updates keep arriving
 * ({@code RecallciniteChannelProgressPayload} is sent every server tick while channeling); a gap of
 * a few client ticks with no update means the channel ended (released or completed), so the overlay
 * hides itself without needing an explicit "stop" message.
 */
public final class ClientRecallciniteState {

    private static final int STALE_AFTER_TICKS = 4;

    private static final ClientRequestSlot<Boolean> OPEN_BIND_CONFIRM = new ClientRequestSlot<>();
    private static volatile float channelFraction;
    private static volatile int ticksSinceProgressUpdate = Integer.MAX_VALUE;

    private ClientRecallciniteState() {
    }

    public static void requestOpenBindConfirm() {
        OPEN_BIND_CONFIRM.request(true);
    }

    public static boolean takePendingOpenBindConfirm() {
        return OPEN_BIND_CONFIRM.takePending().orElse(false);
    }

    public static void onChannelProgress(float fraction) {
        channelFraction = fraction;
        ticksSinceProgressUpdate = 0;
    }

    /** Called once per client tick (LyfeModClient#onClientTick). */
    public static void tick() {
        if (ticksSinceProgressUpdate < Integer.MAX_VALUE) {
            ticksSinceProgressUpdate++;
        }
    }

    public static boolean isChannelActive() {
        return ticksSinceProgressUpdate < STALE_AFTER_TICKS;
    }

    public static float channelFraction() {
        return channelFraction;
    }
}
