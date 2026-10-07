package com.github.cerealklla.lyfe.swim;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.github.cerealklla.lyfe.LyfeMod;
import com.github.cerealklla.lyfe.api.Lyfe;
import com.github.cerealklla.lyfe.registration.ModAttachments;
import com.github.cerealklla.lyfe.skill.Skills;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The decoupled-air mechanism (design doc Section 5's Swimmer entry): vanilla's real air supply
 * keeps its own hardcoded 300-tick cap and {@code -20}-threshold drowning check untouched as far as
 * its own internals go, but the real value is force-pinned to a safe constant every tick so that
 * parallel system never actually fires (confirmed against the decompiled {@code
 * LivingEntity#baseTick}/{@code shouldTakeDrowningDamage} source: vanilla decrements by a flat 1
 * per tick while submerged, so resetting to {@link AirConstants#REAL_AIR_PIN} every tick-end means
 * it never crosses -20). {@link PlayerAir#getTrueAir()} is what actually matters -- it drives the
 * custom overlay, the Swimmer-scaled capacity/recovery, and this class's own custom drowning-damage
 * port.
 *
 * <p>Unlike {@code .hunger.HungerListener}, air depletion needs no real-value delta measurement --
 * vanilla's own decrement while submerged is a simple deterministic -1/tick (no multi-source
 * exhaustion accumulator the way hunger has), so this drives {@link PlayerAir} directly from a
 * per-tick submersion check instead.
 *
 * <p>Banked XP is paid out <b>incrementally, one bubble's worth at a time as each bubble actually
 * finishes refilling</b> while breathing (corrected 2026-10-02 -- the original design paid the
 * whole bank the instant breathing resumed, which could grant XP for bubbles that hadn't actually
 * recovered yet if the dive continued or drowning damage landed before the real refill caught up).
 */
public final class SwimmerListener {

    private static final Identifier SWIM_SPEED_MODIFIER_ID =
            Identifier.fromNamespaceAndPath(LyfeMod.MODID, "swimmer_speed");

    // Session-only drown-damage interval timer and previous-tick submersion state, one per player --
    // mirrors HungerListener's own session-only tickTimers map; neither needs persistence.
    private final Map<UUID, Integer> drownTimers = new HashMap<>();
    private final Set<UUID> wasSubmerged = new HashSet<>();

    // Set only when drowning damage actually wipes a nonzero bank (see handleDrowning) -- 2026-10-03
    // playtest correction: the "forgot your technique" message was firing on ANY surfacing with a
    // zero bank, including a trivial dip (e.g. jumping in water to put out fire) that never banked
    // anything to lose in the first place. Now it only fires when something was genuinely lost.
    private final Set<UUID> lostFocusThisDive = new HashSet<>();

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        PlayerAir air = player.getData(ModAttachments.PLAYER_AIR);
        int currentMax = currentMaxAir(player);
        boolean submerged = isSubmerged(player);
        UUID playerId = player.getUUID();

        if (submerged) {
            int beforeBubbles = air.getTrueAir() / AirConstants.TICKS_PER_BUBBLE;
            air.applyRealAirDrop(1, currentMax);
            int afterBubbles = air.getTrueAir() / AirConstants.TICKS_PER_BUBBLE;
            if (afterBubbles < beforeBubbles) {
                air.bankXp((beforeBubbles - afterBubbles) * AirConstants.XP_PER_BUBBLE_LOST);
            }
            handleDrowning(player, air);
            wasSubmerged.add(playerId);
        } else {
            int beforeBubbles = air.getTrueAir() / AirConstants.TICKS_PER_BUBBLE;
            air.applyRealAirGain(currentRecoveryRate(player), currentMax);
            int afterBubbles = air.getTrueAir() / AirConstants.TICKS_PER_BUBBLE;
            int bubblesGained = afterBubbles - beforeBubbles;

            // The "forgot your technique" message fires exactly once, on the submerged->surfaced
            // edge, and only if drowning damage actually wiped a real bank this dive (see
            // lostFocusThisDive's own doc) -- not just "the bank happens to be empty."
            if (wasSubmerged.remove(playerId) && lostFocusThisDive.remove(playerId)) {
                player.sendSystemMessage(Component.literal("You forgot your technique in your panic..."));
            }

            // Paid out as each bubble actually finishes refilling (design doc clarification,
            // 2026-10-02), not as one lump the instant breathing resumes -- re-submerging or
            // drowning before the remaining bank is spent means those not-yet-refilled bubbles
            // simply never get paid (see PlayerAir#spendBank/#wipeBank).
            if (bubblesGained > 0 && air.getCachedXp() > 0) {
                long payout = air.spendBank((long) bubblesGained * AirConstants.XP_PER_BUBBLE_LOST);
                Lyfe.addXp(player, Skills.SWIMMER_ID, payout);
            }
            drownTimers.remove(playerId);
        }
        player.syncData(ModAttachments.PLAYER_AIR);

        // Always force the real value back to a safe pin, submerged or not -- see class doc.
        player.setAirSupply(AirConstants.REAL_AIR_PIN);

        applySwimSpeed(player);
    }

    /** Ports vanilla's {@code shouldTakeDrowningDamage} interval, scaled against the true (not real) air value. */
    private void handleDrowning(ServerPlayer player, PlayerAir air) {
        if (air.getTrueAir() > 0) {
            return;
        }
        UUID playerId = player.getUUID();
        int timer = drownTimers.getOrDefault(playerId, 0) + 1;
        if (timer >= AirConstants.DROWN_DAMAGE_TICK_INTERVAL) {
            ServerLevel level = (ServerLevel) player.level();
            player.hurtServer(level, player.damageSources().drown(), 1.0F);
            if (air.getCachedXp() > 0) {
                lostFocusThisDive.add(player.getUUID());
            }
            air.wipeBank(); // Design doc: drowning damage wipes the cache instead of paying it out.
            timer = 0;
        }
        drownTimers.put(playerId, timer);
    }

    /** Same condition vanilla's own {@code canDrownInWater} check uses (ported from the decompiled source). */
    private boolean isSubmerged(ServerPlayer player) {
        if (!player.isEyeInFluid(FluidTags.WATER)) {
            return false;
        }
        // canBreatheUnderwater() is flagged deprecated in favor of a FluidType-based capability
        // check, but vanilla's own LivingEntity#baseTick still calls this exact deprecated method
        // internally for the same condition (confirmed against the decompiled source) -- matching
        // that, not the newer capability API, since this is porting vanilla's own check verbatim.
        if (player.canBreatheUnderwater() || MobEffectUtil.hasWaterBreathing(player)) {
            return false;
        }
        BlockPos eyePos = BlockPos.containing(player.getX(), player.getEyeY(), player.getZ());
        return !player.level().getBlockState(eyePos).is(Blocks.BUBBLE_COLUMN);
    }

    private void applySwimSpeed(ServerPlayer player) {
        AttributeInstance instance = player.getAttribute(NeoForgeMod.SWIM_SPEED);
        if (instance == null) {
            return;
        }
        instance.removeModifier(SWIM_SPEED_MODIFIER_ID);
        double bonus = currentSwimSpeedBonus(player);
        if (bonus > 0.0) {
            instance.addTransientModifier(new AttributeModifier(
                    SWIM_SPEED_MODIFIER_ID, bonus, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    /** Section 5: grows linearly from vanilla's baseline to the design doc's "two bars beyond default" cap as Swimmer levels. */
    public static int currentMaxAir(ServerPlayer player) {
        return currentMaxAir(Lyfe.getLevel(player, Skills.SWIMMER_ID));
    }

    private static int currentRecoveryRate(ServerPlayer player) {
        return currentRecoveryRate(Lyfe.getLevel(player, Skills.SWIMMER_ID));
    }

    private static double currentSwimSpeedBonus(ServerPlayer player) {
        return currentSwimSpeedBonus(Lyfe.getLevel(player, Skills.SWIMMER_ID));
    }

    /** Pure, level-only overload -- used by the Skills screen, which only has a client-side level, not a {@code ServerPlayer}. */
    public static int currentMaxAir(int level) {
        int growth = AirConstants.MAX_AIR_AT_MAX_LEVEL - AirConstants.BASE_MAX_AIR;
        return AirConstants.BASE_MAX_AIR + growth * level / Skills.MAX_LEVEL;
    }

    private static int currentRecoveryRate(int level) {
        int growth = AirConstants.MAX_RECOVERY_PER_TICK - AirConstants.BASE_RECOVERY_PER_TICK;
        return AirConstants.BASE_RECOVERY_PER_TICK + growth * level / Skills.MAX_LEVEL;
    }

    private static double currentSwimSpeedBonus(int level) {
        return AirConstants.MAX_SWIM_SPEED_BONUS * level / Skills.MAX_LEVEL;
    }

    /**
     * Live benefit readout for the Skills screen (common.skill.SkillBenefits) -- expressed in
     * seconds, not raw ticks, per the user's 2026-10-02 request. "Full recovery time" (how long it
     * takes to refill from completely empty) is a more meaningful seconds-based number than the raw
     * "ticks of air gained per tick elapsed" rate, which has no natural seconds equivalent.
     */
    public static java.util.List<String> benefitLines(int level) {
        int maxAirTicks = currentMaxAir(level);
        int recoveryPerTick = currentRecoveryRate(level);
        double maxAirSeconds = maxAirTicks / 20.0;
        double recoverySeconds = maxAirTicks / (double) recoveryPerTick / 20.0;
        return java.util.List.of(
                "Max air: " + String.format(java.util.Locale.ROOT, "%.1f", maxAirSeconds) + " seconds",
                "Full recovery time: " + String.format(java.util.Locale.ROOT, "%.1f", recoverySeconds) + " seconds",
                "Swim speed: +" + String.format(java.util.Locale.ROOT, "%.2f", currentSwimSpeedBonus(level))
        );
    }
}
