package com.github.cerealklla.lyfe.socket;

import java.util.List;

import com.github.cerealklla.lyfe.api.Lyfe;
import com.github.cerealklla.lyfe.gathering.GatheringSkill;
import com.github.cerealklla.lyfe.registration.ModItems;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Implements every {@link RunewordEffect.Type} primitive exactly once (see that class's own doc for
 * why) -- five real NeoForge event hooks cover all 18: {@link LivingIncomingDamageEvent} (both the
 * attacker's weapon/tool ON_HIT_* procs AND the victim's armor ON_DAMAGED_* / LOW_HEALTH_* procs, one
 * event instance serves both directions), {@link LivingDeathEvent} (ON_KILL_*), {@link
 * BlockDropsEvent} (ON_BREAK_BLOCK_*), {@link PlayerTickEvent.Post} (the two PERIODIC_* armor types,
 * throttled via {@code tickCount % intervalTicks}, same shape {@code currency.CoinPurseListener}'s
 * own slot-check throttle already uses).
 */
public final class RunewordEffectListener {

    @SubscribeEvent
    public void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level) || event.getAmount() <= 0) {
            return;
        }
        // Attacker side: the attacker's own mainhand weapon/tool.
        if (event.getSource().getEntity() instanceof ServerPlayer attacker) {
            ItemStack weapon = attacker.getMainHandItem();
            RunewordDefinition active = activeRuneword(weapon);
            if (active != null) {
                applyOnHit(active.effect(), level, attacker, event.getEntity(), event.getAmount());
            }
        }
        // Victim side: every armor piece the damaged entity is wearing.
        if (event.getEntity() instanceof ServerPlayer victim) {
            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                RunewordDefinition active = activeRuneword(victim.getItemBySlot(slot));
                if (active == null) {
                    continue;
                }
                float reduced = applyOnDamaged(active.effect(), level, victim, event.getSource().getEntity(), event.getAmount());
                if (reduced != event.getAmount()) {
                    event.setAmount(reduced);
                }
            }
        }
    }

    private void applyOnHit(RunewordEffect effect, ServerLevel level, ServerPlayer attacker, LivingEntity victim, float damageDealt) {
        switch (effect.type()) {
            case ON_HIT_HEAL_PULSE_AOE -> {
                if (!roll(level, effect.chance())) {
                    return;
                }
                for (Player ally : level.players()) {
                    if (ally.distanceToSqr(victim) <= effect.radius() * effect.radius()) {
                        ally.heal((float) effect.magnitude());
                    }
                }
            }
            case ON_HIT_DAMAGE_PROC -> {
                if (roll(level, effect.chance())) {
                    victim.hurtServer(level, level.damageSources().generic(), (float) effect.magnitude());
                }
            }
            case ON_HIT_SLOW_TARGET -> {
                if (roll(level, effect.chance())) {
                    victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, effect.durationTicks(), (int) effect.magnitude() - 1));
                }
            }
            case ON_HIT_LIFESTEAL_BURST -> {
                if (roll(level, effect.chance())) {
                    attacker.heal((float) (damageDealt * effect.magnitude()));
                }
            }
            case ON_HIT_IGNITE -> {
                if (roll(level, effect.chance())) {
                    victim.igniteForTicks(effect.durationTicks());
                }
            }
            case ON_HIT_SPEED_BURST -> {
                if (roll(level, effect.chance())) {
                    attacker.addEffect(new MobEffectInstance(MobEffects.SPEED, effect.durationTicks(), (int) effect.magnitude() - 1));
                }
            }
            default -> {
                // Not a weapon/tool on-hit type -- nothing to do here.
            }
        }
    }

    /** Returns the (possibly reduced) damage amount -- only {@code LOW_HEALTH_DAMAGE_REDUCTION} changes it. */
    private float applyOnDamaged(RunewordEffect effect, ServerLevel level, ServerPlayer victim, Entity attackerEntity, float amount) {
        switch (effect.type()) {
            case ON_DAMAGED_REFLECT -> {
                if (roll(level, effect.chance()) && attackerEntity instanceof LivingEntity attacker) {
                    attacker.hurtServer(level, level.damageSources().generic(), (float) (amount * effect.magnitude()));
                }
            }
            case ON_DAMAGED_HEAL -> {
                if (roll(level, effect.chance())) {
                    victim.heal((float) effect.magnitude());
                }
            }
            case ON_DAMAGED_SPEED_BURST -> {
                if (roll(level, effect.chance())) {
                    victim.addEffect(new MobEffectInstance(MobEffects.SPEED, effect.durationTicks(), (int) effect.magnitude() - 1));
                }
            }
            case LOW_HEALTH_DAMAGE_REDUCTION -> {
                if (victim.getHealth() / victim.getMaxHealth() < effect.secondary()) {
                    return (float) (amount * (1.0 - effect.magnitude()));
                }
            }
            default -> {
                // Not an armor ON_DAMAGED_* / LOW_HEALTH_* type.
            }
        }
        return amount;
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level) || !(event.getSource().getEntity() instanceof ServerPlayer killer)) {
            return;
        }
        for (ItemStack stack : List.of(killer.getMainHandItem())) {
            RunewordDefinition active = activeRuneword(stack);
            if (active == null) {
                continue;
            }
            RunewordEffect effect = active.effect();
            switch (effect.type()) {
                case ON_KILL_EXPLOSION -> {
                    if (!roll(level, effect.chance())) {
                        continue;
                    }
                    for (LivingEntity nearby : level.getEntitiesOfClass(LivingEntity.class,
                            event.getEntity().getBoundingBox().inflate(effect.radius()))) {
                        if (nearby != event.getEntity() && nearby instanceof net.minecraft.world.entity.monster.Enemy) {
                            nearby.hurtServer(level, level.damageSources().generic(), (float) effect.magnitude());
                        }
                    }
                }
                case ON_KILL_XP_BURST -> {
                    if (roll(level, effect.chance())) {
                        Lyfe.addXp(killer, com.github.cerealklla.lyfe.skill.Skills.SOCKETEER_ID, (long) effect.magnitude());
                    }
                }
                case ON_KILL_GOLD_BURST -> {
                    if (roll(level, effect.chance())) {
                        killer.level().addFreshEntity(new ItemEntity(killer.level(), killer.getX(), killer.getY(), killer.getZ(),
                                new ItemStack(Items.GOLD_NUGGET, (int) effect.magnitude())));
                    }
                }
                default -> {
                    // Not an on-kill type.
                }
            }
        }
    }

    @SubscribeEvent
    public void onBlockDrops(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack tool = player.getMainHandItem();
        RunewordDefinition active = activeRuneword(tool);
        if (active == null) {
            return;
        }
        RunewordEffect effect = active.effect();
        ServerLevel level = event.getLevel();
        switch (effect.type()) {
            case ON_BREAK_BLOCK_BONUS_YIELD -> {
                if (roll(level, effect.chance()) && !event.getDrops().isEmpty()) {
                    ItemEntity original = event.getDrops().get(0);
                    event.getDrops().add(new ItemEntity(level, original.getX(), original.getY(), original.getZ(), original.getItem().copy()));
                }
            }
            case ON_BREAK_BLOCK_HASTE_BURST -> {
                if (roll(level, effect.chance())) {
                    player.addEffect(new MobEffectInstance(MobEffects.HASTE, effect.durationTicks(), (int) effect.magnitude() - 1));
                }
            }
            case ON_BREAK_BLOCK_XP_BURST -> {
                if (roll(level, effect.chance())) {
                    GatheringSkill skill = GatheringSkill.forBlock(event.getState());
                    if (skill != null) {
                        Lyfe.addXp(player, skill.skillId(), (long) effect.magnitude());
                    }
                }
            }
            default -> {
                // Not an on-break type.
            }
        }
    }

    private static final int PERIODIC_CHECK_INTERVAL_TICKS = 20;

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % PERIODIC_CHECK_INTERVAL_TICKS != 0) {
            return;
        }
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            RunewordDefinition active = activeRuneword(player.getItemBySlot(slot));
            if (active == null) {
                continue;
            }
            RunewordEffect effect = active.effect();
            if (effect.durationTicks() <= 0 || player.tickCount % effect.durationTicks() != 0) {
                continue;
            }
            switch (effect.type()) {
                case PERIODIC_SELF_REGEN -> player.heal((float) effect.magnitude());
                case PERIODIC_AREA_BUFF_ALLIES -> {
                    if (player.level() instanceof ServerLevel level) {
                        for (Player ally : level.players()) {
                            if (ally.distanceToSqr(player) <= effect.radius() * effect.radius()) {
                                ally.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, (int) effect.magnitude() - 1));
                            }
                        }
                    }
                }
                default -> {
                    // Not a periodic type.
                }
            }
        }
    }

    private static boolean roll(ServerLevel level, double chance) {
        return level.getRandom().nextDouble() < chance;
    }

    /** {@code null} if {@code stack} isn't socketable, has no SocketData, or no Runeword currently matches its socket sequence. */
    private static RunewordDefinition activeRuneword(ItemStack stack) {
        EquipmentCategory category = EquipmentCategory.of(stack);
        if (category == null) {
            return null;
        }
        SocketData data = stack.getOrDefault(ModItems.SOCKET_DATA.get(), SocketData.EMPTY);
        if (data.sockets().isEmpty()) {
            return null;
        }
        return ServerRunewordStore.match(category, data.sockets());
    }
}
