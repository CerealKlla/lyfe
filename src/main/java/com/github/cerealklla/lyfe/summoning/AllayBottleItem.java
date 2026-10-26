package com.github.cerealklla.lyfe.summoning;

import java.util.Optional;
import java.util.function.Consumer;

import com.github.cerealklla.lyfe.LyfeMod;
import com.github.cerealklla.lyfe.registration.ModEntities;
import com.github.cerealklla.lyfe.registration.ModItems;
import com.github.cerealklla.lyfe.registration.ModMenus;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * Allay in a Bottle (design doc Section 27) -- direct mirror of {@link SummoningStoneItem}'s
 * summon/cooldown shape, plus two behaviors it doesn't have: Shift+Right-Click opens the Equip/
 * Gather GUI ({@link AllayBottleMenu}) instead of summoning, and a plain right-click while already
 * summoned **dispels** (re-captures and discards) instead of just refusing -- the user's own
 * explicit "Right clicking will summon or dispel the Allay." A voluntary dispel charges no
 * cooldown at all, only a real death does (see {@link AllayDeathListener}).
 */
public final class AllayBottleItem extends Item {

    public static final long COOLDOWN_MILLIS = 5L * 60L * 1000L; // 5 minutes, explicit spec.
    private static final double SUMMON_BACK_OFFSET_BLOCKS = 2.0;

    public AllayBottleItem(Properties properties) {
        super(properties);
    }

    /**
     * Real user report, 2026-10-26: this always showed the same static two lines regardless of
     * cooldown/summoned state -- the exact same gap {@link SummoningStoneItem#appendHoverText} was
     * already fixed for (2026-10-25), just never ported over here. Now reads {@link AllayBottleData}
     * directly, same as that fix, with one real difference reflected in the text: right-click on an
     * already-summoned Allay **dispels** it (a real, useful action -- see {@link #use}'s own dispel
     * branch), unlike a Summoning Stone's "already summoned" case, which just refuses.
     */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag tooltipFlag) {
        AllayBottleData data = stack.get(ModItems.ALLAY_BOTTLE_DATA.get());
        if (data != null) {
            long now = System.currentTimeMillis();
            if (data.summonedAllayId().isPresent()) {
                builder.accept(Component.literal("Right-click: Dispel the summoned Allay").withStyle(ChatFormatting.AQUA));
                builder.accept(Component.literal("Shift+Right-click: Configure").withStyle(ChatFormatting.AQUA));
                return;
            }
            if (data.onCooldown(now)) {
                long secondsLeft = (data.cooldownEndEpochMillis() - now) / 1000L + 1L;
                builder.accept(Component.literal("Recovering (" + secondsLeft + "s left)").withStyle(ChatFormatting.RED));
                builder.accept(Component.literal("Shift+Right-click: Configure").withStyle(ChatFormatting.AQUA));
                return;
            }
        }
        builder.accept(Component.literal("Right-click: Summon the bound Allay").withStyle(ChatFormatting.AQUA));
        builder.accept(Component.literal("Shift+Right-click: Configure").withStyle(ChatFormatting.AQUA));
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
        AllayBottleData data = stack.get(ModItems.ALLAY_BOTTLE_DATA.get());
        if (data == null) {
            return InteractionResult.FAIL;
        }

        if (player.isShiftKeyDown()) {
            serverPlayer.openMenu(new SimpleMenuProvider(
                    (windowId, inventory, opener) -> new AllayBottleMenu(ModMenus.ALLAY_BOTTLE.get(), windowId, inventory),
                    Component.literal("Allay")));
            return InteractionResult.SUCCESS_SERVER;
        }

        if (data.summonedAllayId().isPresent()) {
            Entity existing = serverLevel.getEntity(data.summonedAllayId().get());
            if (existing instanceof Allay allay && allay.isAlive()) {
                dispel(serverLevel, stack, data, allay);
                return InteractionResult.SUCCESS;
            }
        }

        long now = System.currentTimeMillis();
        if (data.onCooldown(now)) {
            long secondsLeft = (data.cooldownEndEpochMillis() - now) / 1000L + 1L;
            player.sendSystemMessage(Component.literal("This Allay is recovering (" + secondsLeft + "s left)."));
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
        if (!(spawned instanceof Allay allay)) {
            player.sendSystemMessage(Component.literal("Something went wrong summoning that Allay."));
            return InteractionResult.FAIL;
        }
        allay.setData(com.github.cerealklla.lyfe.registration.ModAttachments.SUMMONED_ALLAY_OWNER, new SummonedAllayOwner(player.getUUID()));
        // Always wins over whatever name happened to be baked into the stale savedState NBT from
        // bind time -- displayName is the single source of truth for the name going forward, so a
        // later GUI rename (which only ever updates displayName, not savedState) still applies on
        // every future summon, not just the first one after a rename.
        allay.setCustomName(Component.literal(data.displayName()));
        AllayEquipment.applyEquipAndGather(allay, data.equipItem(), data.gatherItem(), player.getUUID());
        AllayGoalInjectionListener.reassessSpellGoal(allay);

        stack.set(ModItems.ALLAY_BOTTLE_DATA.get(), data.withSummonedAllayId(Optional.of(allay.getUUID())));
        return InteractionResult.SUCCESS;
    }

    private void dispel(ServerLevel level, ItemStack stack, AllayBottleData data, Allay allay) {
        var capture = AllayCapture.capture(allay, level);
        stack.set(ModItems.ALLAY_BOTTLE_DATA.get(), data
                .withSavedState(capture)
                .withSummonedAllayId(Optional.empty()));
        allay.discard();
    }
}
