package com.github.cerealklla.lyfe.companion;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import com.github.cerealklla.lyfe.registration.ModItems;
import com.github.cerealklla.lyfe.summoning.SummoningDistanceTicker;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * Tavern Companion Token (Settlemynts' design-document.md Section 17) -- "hiring one of these NPCs
 * will give the player a companion summoning token, same concept to how Allays work." Mirrors {@code
 * summoning.AllayBottleItem}'s right-click summon/dispel shape, including its Shift+Right-click GUI
 * -- the first pass left this out on the (wrong, 2026-10-31 real user correction) assumption that a
 * Tavern Companion's gear needed no separate way to change it after hiring. {@link CompanionTokenMenu}
 * is the real equivalent, 6 equipment slots instead of Allay's flat equip/gather pair.
 *
 * <p><b>Rebuilt 2026-10-31</b> to resolve the companion's {@code EntityType} by registry id and
 * apply its equipment/attributes/name directly, instead of a captured-NBT {@code
 * EntityType#loadEntityRecursive} round-trip -- see {@link CompanionTokenData}'s own doc for why.
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

        if (player.isShiftKeyDown()) {
            serverPlayer.openMenu(new net.minecraft.world.SimpleMenuProvider(
                    (windowId, inventory, opener) -> new CompanionTokenMenu(
                            com.github.cerealklla.lyfe.registration.ModMenus.COMPANION_TOKEN.get(), windowId, inventory),
                    Component.literal(data.displayName())));
            return InteractionResult.SUCCESS_SERVER;
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

        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(data.entityTypeId()).orElse(null);
        if (type == null || !(type.create(serverLevel, EntitySpawnReason.MOB_SUMMONED) instanceof LivingEntity companion)) {
            player.sendSystemMessage(Component.literal("Something went wrong summoning " + data.displayName() + "."));
            return InteractionResult.FAIL;
        }

        var back = player.getLookAngle().scale(-SUMMON_BACK_OFFSET_BLOCKS);
        companion.snapTo(player.getX() + back.x, player.getY(), player.getZ() + back.z, player.getYRot(), 0.0F);
        applyLoadout(companion, data);
        serverLevel.addFreshEntity(companion);
        companion.setData(com.github.cerealklla.lyfe.registration.ModAttachments.SUMMONED_COMPANION_OWNER,
                new SummonedCompanionOwner(player.getUUID()));
        companion.setCustomName(Component.literal(data.displayName()));

        stack.set(ModItems.COMPANION_TOKEN_DATA.get(), data.withSummonedCompanionId(Optional.of(companion.getUUID())));
        return InteractionResult.SUCCESS;
    }

    private static void applyLoadout(LivingEntity companion, CompanionTokenData data) {
        for (CompanionTokenData.EquipmentEntry entry : data.equipment()) {
            companion.setItemSlot(entry.slot(), entry.stack().copy());
        }
        if (companion instanceof net.minecraft.world.entity.Mob mob) {
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                mob.setDropChance(slot, 0.0F);
            }
        }
        var maxHealthAttr = companion.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealthAttr != null) {
            maxHealthAttr.setBaseValue(data.maxHealth());
        }
        companion.setHealth((float) data.maxHealth());
        var attackAttr = companion.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attackAttr != null) {
            attackAttr.setBaseValue(data.attackDamage());
        }
    }

    private void dispel(ServerLevel level, ItemStack stack, CompanionTokenData data, LivingEntity companion) {
        List<CompanionTokenData.EquipmentEntry> equipment = new ArrayList<>();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack equipped = companion.getItemBySlot(slot);
            if (!equipped.isEmpty()) {
                equipment.add(new CompanionTokenData.EquipmentEntry(slot, equipped.copy()));
            }
        }
        double maxHealth = companion.getAttributeValue(Attributes.MAX_HEALTH);
        double attackDamage = companion.getAttribute(Attributes.ATTACK_DAMAGE) != null
                ? companion.getAttributeValue(Attributes.ATTACK_DAMAGE) : 0.0;
        stack.set(ModItems.COMPANION_TOKEN_DATA.get(), data
                .withEquipmentAndStats(equipment, maxHealth, attackDamage)
                .withSummonedCompanionId(Optional.empty()));
        companion.discard();
    }
}
