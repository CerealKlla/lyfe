package com.github.cerealklla.lyfe.recallcinite;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;

/**
 * A single player's Recallcinite Totem state: where it's bound (if anywhere), when the *recall*
 * cooldown next clears (a plain game-time tick, not enforced via vanilla's own {@code
 * ItemCooldowns} -- see {@link RecallciniteTotemItem}'s own doc for why that approach was ruled
 * out), and which settlements have already paid out Recallcraft bind XP.
 *
 * <p><b>Binding itself has no cooldown</b> (corrected 2026-10-09, real report: "once bound it won't
 * let me bind to a different location... I'd like to be able to freely re-bind the totem, but only
 * get xp once per settlement bound to" -- the original shared bind/recall cooldown meant binding
 * once immediately locked out both re-binding AND recalling for up to an hour). Only {@link
 * RecallciniteListener#onChannelCompleted} (the actual 10s-hold recall) reads/sets the cooldown now;
 * binding is unlimited, with {@link #xpAwardedSettlements} preventing XP-farming via repeated binds
 * at the same settlement instead.
 */
public final class RecallciniteData {

    public static final MapCodec<RecallciniteData> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            GlobalPos.CODEC.optionalFieldOf("bound_location").forGetter(d -> d.boundLocation),
            Codec.LONG.fieldOf("cooldown_end_game_time").forGetter(d -> d.cooldownEndGameTime),
            Codec.list(UUIDUtil.CODEC).optionalFieldOf("xp_awarded_settlements", java.util.List.of())
                    .forGetter(d -> java.util.List.copyOf(d.xpAwardedSettlements)),
            Codec.INT.optionalFieldOf("bound_plot_tier", 0).forGetter(d -> d.boundPlotTier),
            Codec.LONG.optionalFieldOf("cooldown_total_ticks", 0L).forGetter(d -> d.cooldownTotalTicks)
    ).apply(i, RecallciniteData::new));

    private Optional<GlobalPos> boundLocation;
    private long cooldownEndGameTime;
    private final Set<UUID> xpAwardedSettlements;
    // 0 if the bound location isn't a Recallcinite-Stone-zoned plot (or nothing is bound yet), else
    // that plot's own Tier (1-5) -- the "strength" the totem's tooltip shows (2026-10-09, user
    // request). Cached/synced here rather than resolved live client-side because resolving it needs
    // a real ServerLevel plot lookup (SettlemyntsStructureBridge), which the client has no access
    // to at all -- see RecallciniteListener#refreshBoundPlotTier for where this gets kept current.
    private int boundPlotTier;
    // The full length of the cooldown just set (not just when it ends) -- needed to compute a
    // fraction-remaining for the custom hotbar swipe overlay (2026-10-09, user request: wanted the
    // vanilla-style swipe visual back after it was removed for blocking interaction -- see
    // RecallciniteTotemItem's own doc). Mirrors vanilla's own ItemCooldowns#getCooldownPercent math
    // exactly, just computed from this persisted/synced data instead of vanilla's cooldown map.
    private long cooldownTotalTicks;

    public RecallciniteData() {
        this(Optional.empty(), 0L, java.util.List.of(), 0, 0L);
    }

    private RecallciniteData(Optional<GlobalPos> boundLocation, long cooldownEndGameTime, java.util.List<UUID> xpAwardedSettlements, int boundPlotTier, long cooldownTotalTicks) {
        this.boundLocation = boundLocation;
        this.cooldownEndGameTime = cooldownEndGameTime;
        this.xpAwardedSettlements = new java.util.HashSet<>(xpAwardedSettlements);
        this.boundPlotTier = boundPlotTier;
        this.cooldownTotalTicks = cooldownTotalTicks;
    }

    public Optional<GlobalPos> boundLocation() {
        return boundLocation;
    }

    public void bind(GlobalPos pos) {
        this.boundLocation = Optional.of(pos);
    }

    public boolean onCooldown(long currentGameTime) {
        return currentGameTime < cooldownEndGameTime;
    }

    public long cooldownEndGameTime() {
        return cooldownEndGameTime;
    }

    public void setCooldown(long currentGameTime, long durationTicks) {
        this.cooldownEndGameTime = currentGameTime + durationTicks;
        this.cooldownTotalTicks = durationTicks;
    }

    /** Fraction of the current cooldown still remaining (1.0 = just started, 0.0 = finished/no cooldown) -- for the hotbar swipe overlay. */
    public float cooldownFractionRemaining(long currentGameTime) {
        if (cooldownTotalTicks <= 0 || !onCooldown(currentGameTime)) {
            return 0.0F;
        }
        return (float) Math.clamp((double) (cooldownEndGameTime - currentGameTime) / cooldownTotalTicks, 0.0, 1.0);
    }

    /** Whether Recallcraft bind XP has already been paid out for a bind at this settlement. */
    public boolean hasAwardedBindXp(UUID settlementCoreId) {
        return xpAwardedSettlements.contains(settlementCoreId);
    }

    public void markBindXpAwarded(UUID settlementCoreId) {
        xpAwardedSettlements.add(settlementCoreId);
    }

    /**
     * Re-opens bind XP eligibility for a settlement -- called after a successful recall (explicit
     * user request, 2026-10-09: "once you actually recall and it goes on cooldown it should allow
     * you to re-bind in that same town again for xp once"). The recall cooldown is the real rate
     * limiter on how often this can happen at all, so "once per settlement, resetting on recall"
     * caps XP farming without needing a separate cooldown of its own.
     */
    public void clearBindXpAwarded(UUID settlementCoreId) {
        xpAwardedSettlements.remove(settlementCoreId);
    }

    public int boundPlotTier() {
        return boundPlotTier;
    }

    public void setBoundPlotTier(int tier) {
        this.boundPlotTier = tier;
    }
}
