package com.github.cerealklla.lyfe.summoning;

import java.util.Optional;
import java.util.function.Consumer;

import com.github.cerealklla.lyfe.LyfeMod;
import com.github.cerealklla.lyfe.registration.ModAttachments;
import com.github.cerealklla.lyfe.registration.ModItems;

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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * The Summoning Stone (design doc Section 26) -- right-click summons the bound mount (a fresh clone
 * spawned from the stone's own {@link SummoningStoneData#savedState}, see {@link
 * MountBindListener}'s own doc for how that's captured), or does nothing while one is already out
 * or on cooldown. Cooldown is wall-clock, same reasoning/mechanism as {@code
 * recallcinite.RecallciniteData} -- never vanilla's own {@code ItemCooldowns}, which would block
 * {@code use()} entirely while active (confirmed real bug there, see that class's own doc).
 */
public final class SummoningStoneItem extends Item {

    public static final long COOLDOWN_MILLIS = 5L * 60L * 1000L; // 5 minutes, explicit spec.
    private static final double SUMMON_BACK_OFFSET_BLOCKS = 2.0;

    public SummoningStoneItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag tooltipFlag) {
        builder.accept(Component.literal("Right-click: Summon the bound mount").withStyle(ChatFormatting.AQUA));
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
        SummoningStoneData data = stack.get(ModItems.SUMMONING_STONE_DATA.get());
        if (data == null) {
            return InteractionResult.FAIL;
        }

        if (data.summonedMountId().isPresent()) {
            Entity existing = serverLevel.getEntity(data.summonedMountId().get());
            if (existing != null && existing.isAlive()) {
                player.sendSystemMessage(Component.literal("That mount is already summoned."));
                return InteractionResult.FAIL;
            }
        }

        long now = System.currentTimeMillis();
        if (data.onCooldown(now)) {
            long secondsLeft = (data.cooldownEndEpochMillis() - now) / 1000L + 1L;
            player.sendSystemMessage(Component.literal("This Summoning Stone is recharging (" + secondsLeft + "s left)."));
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
        if (spawned == null) {
            player.sendSystemMessage(Component.literal("Something went wrong summoning that mount."));
            return InteractionResult.FAIL;
        }
        spawned.setData(ModAttachments.SUMMONED_MOUNT_OWNER, new SummonedMountOwner(player.getUUID()));

        stack.set(ModItems.SUMMONING_STONE_DATA.get(), data.withSummonedMountId(Optional.of(spawned.getUUID())));
        return InteractionResult.SUCCESS;
    }
}
