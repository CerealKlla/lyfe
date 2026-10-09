package com.github.cerealklla.lyfe.recallcinite;

import java.util.function.Consumer;

import com.github.cerealklla.lyfe.registration.ModAttachments;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * The Recallcinite Totem (design doc, 2026-10-09 user request): a permanent, hotbar-slot-0-pinned
 * item that replaces bed-based respawn with an explicit bind/recall mechanic. A quick right-click
 * tap opens a Yes/No bind confirmation ({@link RecallciniteListener#openBindConfirmation}); holding
 * right-click for the full {@link #USE_DURATION_TICKS} (10 real seconds) teleports the player back
 * to their bound location ({@link RecallciniteListener#performRecall}). Releasing partway through
 * (past a brief tap-detection window, before the full hold completes) cancels with no effect. While
 * on cooldown, the effective hold duration is shortened to just past the tap window (see {@link
 * #getUseDuration}'s own doc) so an attempted hold fails fast instead of running the full 10s first.
 *
 * <p>Only RECALL has a cooldown -- binding is free and unlimited (corrected 2026-10-09, real
 * report: binding used to consume a shared cooldown that then blocked both re-binding AND
 * recalling for up to an hour). Deliberately does NOT use vanilla's own {@code
 * Player#getCooldowns()} at all -- a real, confirmed bug, not just a theoretical one:
 * {@code ServerPlayerGameMode#useItem} checks {@code player.getCooldowns().isOnCooldown(itemStack)}
 * and skips calling {@code Item#use} ENTIRELY if true, which blocked every interaction with the
 * totem during the cooldown -- including binding, which was supposed to stay free regardless. An
 * earlier version of this class still set vanilla's cooldown purely for its cosmetic hotbar swipe
 * animation, not realizing that side effect existed; removed outright once this was traced down.
 * The real cooldown lives entirely in {@link RecallciniteData}, a persisted, synced per-player
 * attachment (survives relogin, unlike vanilla's in-memory-only cooldown map; synced so this
 * item's own tooltip can show the real remaining time).
 */
public final class RecallciniteTotemItem extends Item {

    public static final int USE_DURATION_TICKS = 200; // 10 seconds
    public static final int TAP_THRESHOLD_TICKS = 10; // ~0.5s: a quick press reads as "tap to bind," not "released mid-hold"

    public RecallciniteTotemItem(Properties properties) {
        super(properties);
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack itemStack) {
        return ItemUseAnimation.SPYGLASS;
    }

    // Shortened to just past the tap-detection window while on cooldown (real report, 2026-10-09:
    // "while it was on cooldown it still let me hold it the whole 10 seconds... but at the end said
    // it was on cooldown" -- the full duration can never succeed in that state, so making the
    // player commit to a full 10s hold just to be told that at the very end is a real UX gap).
    // Can't resolve this synchronously in use() the way the unbound case was fixed, though -- a
    // bound totem's hold IS still genuinely ambiguous on cooldown (a quick tap for a free rebind is
    // still a completely valid thing to do), so the hold mechanism still has to start. Shortening
    // the duration instead means a real tap still completes normally via releaseUsing (well within
    // this short window), while an attempted hold-for-recall fails fast via the normal
    // finishUsingItem -> performRecall path instead of running the full 10s first.
    @Override
    public int getUseDuration(ItemStack itemStack, LivingEntity user) {
        if (user.getData(ModAttachments.RECALLCINITE_DATA).onCooldown(user.level().getGameTime())) {
            return TAP_THRESHOLD_TICKS + 1;
        }
        return USE_DURATION_TICKS;
    }

    // Deliberately does NOT check the recall cooldown here -- binding (a quick tap) is always free
    // regardless of cooldown state (see RecallciniteData's own doc), and only the channel's outcome
    // (RecallciniteListener#onChannelReleased for a tap, #performRecall for a full hold) can tell
    // which action is actually being attempted. The cooldown is enforced at the point it actually
    // matters -- performRecall sends its own "still recharging" message if a full hold completes
    // while on cooldown.
    //
    // While UNBOUND, this skips the hold mechanism entirely and resolves the bind attempt right
    // here, synchronously, on a plain click -- no startUsingItem() at all (real report, 2026-10-09:
    // going through the hold mechanism here used to cause a genuine stuck-slow-walking bug. Vanilla
    // restarts a use cycle immediately if the mouse is still held when one ends -- same behavior as
    // holding right-click through a stack of food -- so ANY approach that let a channel end while
    // still unbound (a natural duration timeout, or a manual mid-hold cutoff) just looped forever
    // for as long as the button stayed held. There's no hold-vs-tap ambiguity to resolve while
    // unbound anyway -- a recall could never succeed either way, so every unbound click is always a
    // bind attempt, with nothing to wait and see about).
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.CONSUME;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        if (serverPlayer.getData(ModAttachments.RECALLCINITE_DATA).boundLocation().isEmpty()) {
            RecallciniteListener.attemptBind(serverPlayer);
            return InteractionResult.SUCCESS;
        }
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    // elapsed/total are both computed from getUseDuration(itemStack, livingEntity) freshly here,
    // NOT the USE_DURATION_TICKS constant directly -- that constant is only the UNCAPPED duration;
    // while on cooldown the real duration for this session is the shortened one above, and using
    // the wrong total here would badly miscompute both "elapsed" (ticksRemaining counts down from
    // whatever the real duration actually was) and the progress-bar fraction.
    @Override
    public void onUseTick(Level level, LivingEntity livingEntity, ItemStack itemStack, int ticksRemaining) {
        if (level.isClientSide() || !(livingEntity instanceof ServerPlayer serverPlayer)) {
            return;
        }
        int total = getUseDuration(itemStack, livingEntity);
        int elapsed = total - ticksRemaining;
        RecallciniteListener.onChannelTick(serverPlayer, elapsed, total);
    }

    @Override
    public boolean releaseUsing(ItemStack itemStack, Level level, LivingEntity entity, int remainingTime) {
        if (level.isClientSide() || !(entity instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        int elapsed = getUseDuration(itemStack, entity) - remainingTime;
        RecallciniteListener.onChannelReleased(serverPlayer, elapsed);
        return true;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack itemStack, Level level, LivingEntity entity) {
        if (!level.isClientSide() && entity instanceof ServerPlayer serverPlayer) {
            RecallciniteListener.onChannelCompleted(serverPlayer);
        }
        return itemStack;
    }

    // Only the two static instruction lines live here -- a real, CONFIRMED Production crash
    // (2026-10-09, not a theoretical worry) ruled out ever referencing net.minecraft.client.Minecraft
    // from this class, even behind a runtime Dist.isClient() check: NeoForge's registry-event
    // dispatch triggers eager verification of this class's own methods during mod loading, which
    // resolves every referenced type regardless of whether that branch would ever actually run on
    // this physical side -- NoClassDefFoundError: net/minecraft/client/player/LocalPlayer, server
    // failed to boot at all. The dynamic "Strength"/cooldown lines moved to a genuine
    // ItemTooltipEvent listener in LyfeModClient (a real @Mod(dist = Dist.CLIENT) class, the only
    // safe place for this), which also conveniently hands over the viewing Player directly via
    // event.getEntity() instead of needing Minecraft.getInstance().player at all.
    @Override
    public void appendHoverText(ItemStack itemStack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag tooltipFlag) {
        builder.accept(Component.literal("Right click in a town to bind this stone to the location.").withStyle(ChatFormatting.GRAY));
        builder.accept(Component.literal("Right click + hold for 10 seconds to recall to this location from anywhere in the world.").withStyle(ChatFormatting.GRAY));
    }
}
