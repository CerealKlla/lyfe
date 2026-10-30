package com.github.cerealklla.lyfe.summoning;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import com.github.cerealklla.lyfe.LyfeMod;
import com.github.cerealklla.lyfe.registration.ModAttachments;
import com.github.cerealklla.lyfe.registration.ModItems;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
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

    /**
     * Real user report, 2026-10-25: "it may be even better to create a custom event..." (the
     * broader cooldown architecture suggestion) came after noticing the tooltip said nothing about
     * cooldown state at all -- it always showed the same "right-click to summon" line regardless of
     * whether that would actually work. Now reads {@link SummoningStoneData} directly (same real
     * data {@link #use} itself checks) so the tooltip matches the item's actual current state.
     */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag tooltipFlag) {
        SummoningStoneData data = stack.get(ModItems.SUMMONING_STONE_DATA.get());
        if (data != null) {
            appendBoundStats(data.savedState(), builder);
            long now = System.currentTimeMillis();
            if (data.summonedMountId().isPresent()) {
                builder.accept(Component.literal("Already summoned").withStyle(ChatFormatting.GRAY));
                return;
            }
            if (data.onCooldown(now)) {
                long secondsLeft = (data.cooldownEndEpochMillis() - now) / 1000L + 1L;
                builder.accept(Component.literal("Recharging (" + secondsLeft + "s left)").withStyle(ChatFormatting.RED));
                return;
            }
        }
        builder.accept(Component.literal("Right-click: Summon the bound mount").withStyle(ChatFormatting.AQUA));
    }

    /**
     * Real user request, 2026-10-29: "I'd also like the tooltip for the Summoning Stone to clearly
     * list the stats for the entity bound to it: Movement speed, Jump strength, Max health" -- came
     * right after confirming horses roll randomized values for exactly these three attributes on
     * spawn, so a Summoning Stone's tooltip is the natural place to see what you actually captured
     * without having to re-summon and compare. Reads the real saved {@code "attributes"} list {@link
     * SummoningStoneData#capture} already writes (vanilla's own {@code LivingEntity#addAdditionalSaveData}
     * shape, {@code AttributeInstance.Packed.LIST_CODEC}) rather than spawning anything -- no live
     * entity needed just to read its base attribute values.
     */
    private static void appendBoundStats(CompoundTag savedState, Consumer<Component> builder) {
        List<AttributeInstance.Packed> packed = AttributeInstance.Packed.LIST_CODEC
                .parse(NbtOps.INSTANCE, savedState.getListOrEmpty("attributes"))
                .result().orElse(List.of());
        if (packed.isEmpty()) {
            return;
        }
        appendStat(builder, packed, Attributes.MOVEMENT_SPEED, "Movement Speed", 3);
        appendStat(builder, packed, Attributes.JUMP_STRENGTH, "Jump Strength", 3);
        appendStat(builder, packed, Attributes.MAX_HEALTH, "Max Health", 1);
    }

    private static void appendStat(Consumer<Component> builder, List<AttributeInstance.Packed> packed,
            Holder<Attribute> attribute, String label, int decimals) {
        for (AttributeInstance.Packed entry : packed) {
            if (entry.attribute().value() == attribute.value()) {
                builder.accept(Component.literal(label + ": " + String.format("%." + decimals + "f", entry.baseValue()))
                        .withStyle(ChatFormatting.GREEN));
                return;
            }
        }
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
            // Real bug fix, 2026-10-30 -- see SummonDimensionChangeListener's own doc for the full
            // incident this is part of. serverLevel.getEntity(id) only ever searches the PLAYER's
            // own current dimension; if the summoned mount is actually still alive in a *different*
            // one (e.g. the player walked through a portal without it, or re-clicked before the
            // dimension-change despawn's own deferred tick ran), this used to read as "nothing
            // there" and silently fall through to summoning a brand-new mount on top of the still-
            // live original -- a direct, immediate duplication path, independent of any ticker/event
            // timing. Searching every loaded dimension (SummoningDistanceTicker#resolveTracked)
            // finds it regardless of which one it's actually in.
            Entity existing = SummoningDistanceTicker.resolveTracked(serverLevel.getServer(), data.summonedMountId().get());
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
