package com.github.cerealklla.lyfe.magician;

import com.github.cerealklla.lyfe.registration.ModEntities;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Spark of Life's projectile (design doc Section 24: "Fires a small ball of light, same speed/arc
 * as an arrow from a bow. Upon hitting an Enemy deals damage similar to basic bow+arrow; upon
 * hitting an ally, instantly heals 1 heart (overheals to absorption if full health)"). A plain
 * {@link ThrowableItemProjectile} rather than an {@code AbstractArrow} -- gets the same ballistic
 * physics (gravity/arc) and {@code shootFromRotation} launch helper either way (both live on the
 * shared {@code Projectile} base class, confirmed against the decompiled source), but a much
 * simpler single {@link #onHitEntity} override point with no pickup/pierce/enchantment machinery
 * this spell has no use for. Visually rendered by vanilla's own {@code ThrownItemRenderer} (a
 * spinning, full-bright Glowstone Dust) -- "a small ball of light" with no new client rendering
 * code needed, same discipline as {@code npc.NpcHealthBar}'s nameplate reuse.
 *
 * <p><b>Ally/enemy resolution is alignment-symmetric</b> (explicit user answer, 2026-10-20: "for
 * now, non-hostile. Will expand in future with player party system... I could see Pillagers
 * spawning with these and wanting to heal their fellow pillagers") -- a target is an ally of the
 * caster whenever both are hostile, or both are non-hostile ({@link Enemy}, the same shared
 * hostility marker this suite's {@code guardhouse.GuardSightGoal} already uses). A caster with no
 * owner at all (shouldn't normally happen -- every cast comes from {@code SparkOfLifeCasting}
 * with a real caster) is treated as non-hostile.
 */
public final class SparkOfLifeProjectile extends ThrowableItemProjectile {

    // Matches AbstractArrow's own default baseDamage (2.0) -- combined with this spell's launch
    // speed (3.0, matching a fully-drawn bow shot) this reproduces vanilla's own "ceil(velocity *
    // baseDamage)" formula for ~6 damage, the same ballpark a real unenchanted bow deals.
    private static final double BASE_DAMAGE = 2.0;
    private static final float HEAL_AMOUNT = 2.0F; // 1 heart

    public SparkOfLifeProjectile(EntityType<SparkOfLifeProjectile> type, Level level) {
        super(type, level);
    }

    public SparkOfLifeProjectile(Level level, LivingEntity owner) {
        super(ModEntities.SPARK_OF_LIFE_PROJECTILE.get(), owner, level, new ItemStack(Items.GLOWSTONE_DUST));
    }

    @Override
    protected Item getDefaultItem() {
        return Items.GLOWSTONE_DUST;
    }

    private static boolean hostile(Entity entity) {
        return entity instanceof Enemy;
    }

    @Override
    protected void onHitEntity(EntityHitResult hitResult) {
        if (!(hitResult.getEntity() instanceof LivingEntity target)) {
            discard();
            return;
        }
        Entity owner = getOwner();
        boolean casterHostile = owner != null && hostile(owner);
        boolean targetHostile = hostile(target);

        if (casterHostile == targetHostile) {
            boolean healedAnything = healAlly(target);
            if (healedAnything && owner instanceof ServerPlayer player) {
                MagicianListener.grantProgress(player, SpellIds.SPARK_OF_LIFE, SpellProficiencyCurve.PROGRESS_PER_LEVEL);
            }
        } else {
            double velocity = getDeltaMovement().length();
            int damage = (int) Math.ceil(velocity * BASE_DAMAGE);
            DamageSource damageSource = damageSources().indirectMagic(this, owner);
            target.hurtOrSimulate(damageSource, damage);
        }
        discard();
    }

    /**
     * Heals {@code target} 1 heart, overflowing into Absorption past full health (explicit spec) --
     * returns whether anything actually happened, since "surpassing max absorption with no effect"
     * must NOT grant Spell Proficiency (see {@code SparkOfLifeProjectile#onHitEntity}'s caller).
     */
    private boolean healAlly(LivingEntity target) {
        float remaining = HEAL_AMOUNT;
        boolean effective = false;

        float missingHealth = target.getMaxHealth() - target.getHealth();
        if (missingHealth > 0.0F) {
            float toHeal = Math.min(remaining, missingHealth);
            target.heal(toHeal);
            remaining -= toHeal;
            effective = true;
        }
        if (remaining > 0.0F) {
            double maxAbsorption = target.getAttributeValue(Attributes.MAX_ABSORPTION);
            float currentAbsorption = target.getAbsorptionAmount();
            double room = maxAbsorption - currentAbsorption;
            if (room > 0.0) {
                float toAdd = (float) Math.min(room, remaining);
                target.setAbsorptionAmount(currentAbsorption + toAdd);
                effective = true;
            }
        }
        return effective;
    }
}
