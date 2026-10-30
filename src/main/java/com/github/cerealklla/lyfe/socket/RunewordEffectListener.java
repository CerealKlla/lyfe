package com.github.cerealklla.lyfe.socket;

import java.util.List;

import com.github.cerealklla.lyfe.api.Lyfe;
import com.github.cerealklla.lyfe.combat.TaggedEffectDamage;
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
            debugLog("onIncomingDamage bailed early -- serverLevel={}, amount={}",
                    event.getEntity().level() instanceof ServerLevel, event.getAmount());
            return;
        }
        // Attacker side: both hands -- a chance-on-hit Weapon/Tool Runeword procs the same
        // regardless of which hand it's actually held in (explicit user spec, 2026-10-30: no
        // main-hand/off-hand distinction for Runeword purposes, unlike vanilla's own mainhand-only
        // attack animation/damage-source semantics, which this deliberately does not mirror).
        debugLog("onIncomingDamage fired -- sourceEntity={}, directEntity={}, amount={}, victim={}",
                describe(event.getSource().getEntity()), describe(event.getSource().getDirectEntity()),
                event.getAmount(), describe(event.getEntity()));
        if (event.getSource().getEntity() instanceof ServerPlayer attacker) {
            ItemStack mainHand = attacker.getMainHandItem();
            ItemStack offHand = attacker.getOffhandItem();
            debugLog("attacker-side check -- attacker={}, mainHand={} (category={}), offHand={} (category={})",
                    attacker.getName().getString(), mainHand.getItem(), EquipmentCategory.of(mainHand),
                    offHand.getItem(), EquipmentCategory.of(offHand));
            for (ItemStack weapon : List.of(mainHand, offHand)) {
                RunewordDefinition active = activeRuneword(weapon);
                if (active != null) {
                    debugLog("attacker-side match -- item={}, runeword=\"{}\", type={}, chance={}",
                            weapon.getItem(), active.name(), active.effect().type(), active.effect().chance());
                    applyOnHit(active.effect(), active.name(), level, attacker, event.getEntity(), event.getAmount());
                } else if (!weapon.isEmpty()) {
                    debugLog("attacker-side no match -- item={} has no active Runeword", weapon.getItem());
                }
            }
        } else {
            debugLog("attacker-side skipped -- damage source entity is not a ServerPlayer");
        }
        // Victim side: every armor piece the damaged entity is wearing.
        if (event.getEntity() instanceof ServerPlayer victim) {
            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                RunewordDefinition active = activeRuneword(victim.getItemBySlot(slot));
                if (active == null) {
                    continue;
                }
                debugLog("victim-side match -- slot={}, runeword=\"{}\", type={}, chance={}",
                        slot, active.name(), active.effect().type(), active.effect().chance());
                float reduced = applyOnDamaged(active.effect(), active.name(), level, victim, event.getSource().getEntity(), event.getAmount());
                if (reduced != event.getAmount()) {
                    event.setAmount(reduced);
                }
            }
        }
    }

    /** Temporary diagnostic (2026-10-30, live report: Emberkiss's Ignite proc never once observed on an off-hand Focus via melee or Spark of Life). Logs every Runeword proc decision point across every trigger -- melee/spell hits, armor reactions, kills, block breaks, periodic ticks -- with an explicit succeed/fail outcome. Remove once the real cause is confirmed and fixed. */
    private static void debugLog(String format, Object... args) {
        com.github.cerealklla.lyfe.LyfeMod.LOGGER.info("[RunewordDebug] " + format, args);
    }

    private static String describe(Entity entity) {
        return entity == null ? "null" : entity.getClass().getSimpleName() + "(" + entity.getName().getString() + ")";
    }

    private void applyOnHit(RunewordEffect effect, String runewordName, ServerLevel level, ServerPlayer attacker, LivingEntity victim, float damageDealt) {
        switch (effect.type()) {
            case ON_HIT_HEAL_PULSE_AOE -> {
                if (!roll(level, effect.chance(), runewordName, effect.type())) {
                    return;
                }
                for (Player ally : level.players()) {
                    if (ally.distanceToSqr(victim) <= effect.radius() * effect.radius()) {
                        TaggedEffectDamage.receiveHeal(ally, effect.combinedTags(), (float) effect.magnitude());
                    }
                }
            }
            case ON_HIT_DAMAGE_PROC -> {
                if (roll(level, effect.chance(), runewordName, effect.type())) {
                    TaggedEffectDamage.receiveDamage(victim, level.damageSources().generic(), effect.combinedTags(), (float) effect.magnitude());
                }
            }
            case ON_HIT_SLOW_TARGET -> {
                if (roll(level, effect.chance(), runewordName, effect.type())) {
                    victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, effect.durationTicks(), (int) effect.magnitude() - 1));
                }
            }
            case ON_HIT_LIFESTEAL_BURST -> {
                if (roll(level, effect.chance(), runewordName, effect.type())) {
                    TaggedEffectDamage.receiveHeal(attacker, effect.combinedTags(), (float) (damageDealt * effect.magnitude()));
                }
            }
            case ON_HIT_IGNITE -> {
                if (roll(level, effect.chance(), runewordName, effect.type())) {
                    victim.igniteForTicks(effect.durationTicks());
                    debugLog("ON_HIT_IGNITE applied -- victim={}, durationTicks={}", describe(victim), effect.durationTicks());
                }
            }
            case ON_HIT_SPEED_BURST -> {
                if (roll(level, effect.chance(), runewordName, effect.type())) {
                    attacker.addEffect(new MobEffectInstance(MobEffects.SPEED, effect.durationTicks(), (int) effect.magnitude() - 1));
                }
            }
            default -> {
                debugLog("applyOnHit -- runeword=\"{}\" type={} is not an attacker on-hit type, nothing to do", runewordName, effect.type());
            }
        }
    }

    /** Returns the (possibly reduced) damage amount -- only {@code LOW_HEALTH_DAMAGE_REDUCTION} changes it. */
    private float applyOnDamaged(RunewordEffect effect, String runewordName, ServerLevel level, ServerPlayer victim, Entity attackerEntity, float amount) {
        switch (effect.type()) {
            case ON_DAMAGED_REFLECT -> {
                if (roll(level, effect.chance(), runewordName, effect.type()) && attackerEntity instanceof LivingEntity attacker) {
                    TaggedEffectDamage.receiveDamage(attacker, level.damageSources().generic(), effect.combinedTags(), (float) (amount * effect.magnitude()));
                }
            }
            case ON_DAMAGED_HEAL -> {
                if (roll(level, effect.chance(), runewordName, effect.type())) {
                    TaggedEffectDamage.receiveHeal(victim, effect.combinedTags(), (float) effect.magnitude());
                }
            }
            case ON_DAMAGED_SPEED_BURST -> {
                if (roll(level, effect.chance(), runewordName, effect.type())) {
                    victim.addEffect(new MobEffectInstance(MobEffects.SPEED, effect.durationTicks(), (int) effect.magnitude() - 1));
                }
            }
            case LOW_HEALTH_DAMAGE_REDUCTION -> {
                boolean lowHealth = victim.getHealth() / victim.getMaxHealth() < effect.secondary();
                debugLog("LOW_HEALTH_DAMAGE_REDUCTION check -- runeword=\"{}\", healthFraction={}, threshold={}, lowHealth={}",
                        runewordName, victim.getHealth() / victim.getMaxHealth(), effect.secondary(), lowHealth);
                if (lowHealth) {
                    return (float) (amount * (1.0 - effect.magnitude()));
                }
            }
            default -> {
                debugLog("applyOnDamaged -- runeword=\"{}\" type={} is not an armor ON_DAMAGED_*/LOW_HEALTH_* type, nothing to do", runewordName, effect.type());
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
            debugLog("onDeath -- killer={}, item={}, runeword=\"{}\", type={}, chance={}",
                    killer.getName().getString(), stack.getItem(), active.name(), effect.type(), effect.chance());
            switch (effect.type()) {
                case ON_KILL_EXPLOSION -> {
                    if (!roll(level, effect.chance(), active.name(), effect.type())) {
                        continue;
                    }
                    for (LivingEntity nearby : level.getEntitiesOfClass(LivingEntity.class,
                            event.getEntity().getBoundingBox().inflate(effect.radius()))) {
                        if (nearby != event.getEntity() && nearby instanceof net.minecraft.world.entity.monster.Enemy) {
                            TaggedEffectDamage.receiveDamage(nearby, level.damageSources().generic(), effect.combinedTags(), (float) effect.magnitude());
                        }
                    }
                }
                case ON_KILL_XP_BURST -> {
                    if (roll(level, effect.chance(), active.name(), effect.type())) {
                        Lyfe.addXp(killer, com.github.cerealklla.lyfe.skill.Skills.SOCKETEER_ID, (long) effect.magnitude());
                    }
                }
                case ON_KILL_GOLD_BURST -> {
                    if (roll(level, effect.chance(), active.name(), effect.type())) {
                        killer.level().addFreshEntity(new ItemEntity(killer.level(), killer.getX(), killer.getY(), killer.getZ(),
                                new ItemStack(Items.GOLD_NUGGET, (int) effect.magnitude())));
                    }
                }
                default -> {
                    debugLog("onDeath -- runeword=\"{}\" type={} is not an on-kill type, nothing to do", active.name(), effect.type());
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
        debugLog("onBlockDrops -- player={}, tool={}, runeword=\"{}\", type={}, chance={}",
                player.getName().getString(), tool.getItem(), active.name(), effect.type(), effect.chance());
        switch (effect.type()) {
            case ON_BREAK_BLOCK_BONUS_YIELD -> {
                if (roll(level, effect.chance(), active.name(), effect.type()) && !event.getDrops().isEmpty()) {
                    ItemEntity original = event.getDrops().get(0);
                    event.getDrops().add(new ItemEntity(level, original.getX(), original.getY(), original.getZ(), original.getItem().copy()));
                }
            }
            case ON_BREAK_BLOCK_HASTE_BURST -> {
                if (roll(level, effect.chance(), active.name(), effect.type())) {
                    player.addEffect(new MobEffectInstance(MobEffects.HASTE, effect.durationTicks(), (int) effect.magnitude() - 1));
                }
            }
            case ON_BREAK_BLOCK_XP_BURST -> {
                if (roll(level, effect.chance(), active.name(), effect.type())) {
                    GatheringSkill skill = GatheringSkill.forBlock(event.getState());
                    if (skill != null) {
                        Lyfe.addXp(player, skill.skillId(), (long) effect.magnitude());
                    }
                }
            }
            default -> {
                debugLog("onBlockDrops -- runeword=\"{}\" type={} is not an on-break type, nothing to do", active.name(), effect.type());
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
            debugLog("onPlayerTick -- player={}, slot={}, runeword=\"{}\", type={}",
                    player.getName().getString(), slot, active.name(), effect.type());
            switch (effect.type()) {
                case PERIODIC_SELF_REGEN -> TaggedEffectDamage.receiveHeal(player, effect.combinedTags(), (float) effect.magnitude());
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
                    debugLog("onPlayerTick -- runeword=\"{}\" type={} is not a periodic type, nothing to do", active.name(), effect.type());
                }
            }
        }
    }

    private static boolean roll(ServerLevel level, double chance, String runewordName, RunewordEffect.Type type) {
        double rolled = level.getRandom().nextDouble();
        boolean success = rolled < chance;
        debugLog("roll -- runeword=\"{}\", type={}, chance={}, rolled={}, outcome={}",
                runewordName, type, chance, rolled, success ? "effect succeeded to proc" : "effect failed to proc");
        return success;
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
