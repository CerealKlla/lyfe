package com.github.cerealklla.lyfe.companion;

import java.util.Optional;
import java.util.function.Consumer;

import com.github.cerealklla.lyfe.LyfeMod;
import com.github.cerealklla.lyfe.registration.ModItems;
import com.github.cerealklla.lyfe.summoning.EntityCapture;
import com.github.cerealklla.lyfe.summoning.SummoningDistanceTicker;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * Tavern Companion Token (Settlemynts' design-document.md Section 17) -- "hiring one of these NPCs
 * will give the player a companion summoning token, same concept to how Allays work." Direct mirror
 * of {@code summoning.AllayBottleItem}'s right-click summon/dispel shape, minus the Shift+Right-click
 * Equip/Gather GUI -- a Tavern Companion's gear is already fully baked into its own captured {@code
 * savedState} (see {@link CompanionTokenData}'s own doc), so there's nothing separate to configure.
 */
public final class CompanionTokenItem extends Item {

    public static final long COOLDOWN_MILLIS = 5L * 60L * 1000L; // 5 minutes, same default as Allay in a Bottle.
    private static final double SUMMON_BACK_OFFSET_BLOCKS = 2.0;

    public CompanionTokenItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag tooltipFlag) {
        CompanionTokenData data = stack.get(ModItems.COMPANION_TOKEN_DATA.get());
        if (data != null) {
            long now = System.currentTimeMillis();
            if (data.summonedCompanionId().isPresent()) {
                builder.accept(Component.literal("Right-click: Dispel " + data.displayName()).withStyle(ChatFormatting.AQUA));
                return;
            }
            if (data.onCooldown(now)) {
                long secondsLeft = (data.cooldownEndEpochMillis() - now) / 1000L + 1L;
                builder.accept(Component.literal("Recovering (" + secondsLeft + "s left)").withStyle(ChatFormatting.RED));
                return;
            }
        }
        builder.accept(Component.literal("Right-click: Summon " + (data != null ? data.displayName() : "the bound companion"))
                .withStyle(ChatFormatting.AQUA));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            return InteractionResult.CONSUME;
        }
        if (!(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.PASS;
        }
        ItemStack stack = player.getItemInHand(hand);
        CompanionTokenData data = stack.get(ModItems.COMPANION_TOKEN_DATA.get());
        if (data == null) {
            return InteractionResult.FAIL;
        }

        if (data.summonedCompanionId().isPresent()) {
            Entity existing = SummoningDistanceTicker.resolveTracked(serverLevel.getServer(), data.summonedCompanionId().get());
            if (existing instanceof LivingEntity companion && companion.isAlive() && companion.level() instanceof ServerLevel companionLevel) {
                dispel(companionLevel, stack, data, companion);
                return InteractionResult.SUCCESS;
            }
        }

        long now = System.currentTimeMillis();
        if (data.onCooldown(now)) {
            long secondsLeft = (data.cooldownEndEpochMillis() - now) / 1000L + 1L;
            player.sendSystemMessage(Component.literal(data.displayName() + " is recovering (" + secondsLeft + "s left)."));
            return InteractionResult.FAIL;
        }

        ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(player.problemPath(), LyfeMod.LOGGER);
        Entity spawned;
        try {
            spawned = EntityType.loadEntityRecursive(data.savedState(), serverLevel, EntitySpawnReason.MOB_SUMMONED, entity -> {
                var back = player.getLookAngle().scale(-SUMMON_BACK_OFFSET_BLOCKS);
                entity.snapTo(player.getX() + back.x, player.getY(), player.getZ() + back.z, player.getYRot(), 0.0F);
                serverLevel.addFreshEntity(entity);
                return entity;
            });
        } finally {
            reporter.close();
        }
        if (!(spawned instanceof LivingEntity companion)) {
            player.sendSystemMessage(Component.literal("Something went wrong summoning " + data.displayName() + "."));
            return InteractionResult.FAIL;
        }
        companion.setData(com.github.cerealklla.lyfe.registration.ModAttachments.SUMMONED_COMPANION_OWNER,
                new SummonedCompanionOwner(player.getUUID()));
        // Always wins over whatever name happened to be baked into the stale savedState NBT.
        companion.setCustomName(Component.literal(data.displayName()));

        stack.set(ModItems.COMPANION_TOKEN_DATA.get(), data.withSummonedCompanionId(Optional.of(companion.getUUID())));
        return InteractionResult.SUCCESS;
    }

    private void dispel(ServerLevel level, ItemStack stack, CompanionTokenData data, LivingEntity companion) {
        var capture = EntityCapture.capture(companion, level);
        stack.set(ModItems.COMPANION_TOKEN_DATA.get(), data
                .withSavedState(capture)
                .withSummonedCompanionId(Optional.empty()));
        companion.discard();
    }
}
