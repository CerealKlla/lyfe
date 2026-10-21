package com.github.cerealklla.lyfe.magician;

import com.github.cerealklla.lyfe.registration.ModEntities;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
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
 * spinning Glowstone Dust) plus a particle trail (see {@link #tick()}'s own doc -- the item alone
 * reads as flat, lit by ordinary world light like anything else) -- "a small ball of light" with no
 * new client rendering/model code needed, same discipline as {@code npc.NpcHealthBar}'s nameplate
 * reuse.
 *
 * <p>Grants Spell Proficiency progress -- and, through it, Magician's own level (see {@code
 * MagicianListener}'s class doc: Magician's level is literally the sum of per-spell Proficiency
 * levels) -- only on a successful heal/overheal, never on damaging an enemy (explicit user spec,
 * 2026-10-21: "Spark of Life Proficiency gain when specifically hitting Spark of Life on a target
 * and that target actually being healed/overhealed"). The {@code combat.CombatState}/{@code
 * CombatStateTracker} infrastructure stays, just not wired to either of these.
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

    /**
     * Real report, 2026-10-20: "isn't glowing at all." The class doc's original assumption -- that
     * vanilla's generic {@code ThrownItemRenderer} would render a thrown Glowstone Dust "full-bright"
     * on its own -- was wrong -- confirmed by the report: a thrown item is lit by ordinary world
     * light like anything else, Glowstone Dust has no special glow-rendering treatment as a held/
     * thrown item (only the real Glowstone *block* emits actual light). Writing a bespoke renderer
     * to force full-bright shading is real client rendering work this spell doesn't need -- a
     * particle trail reads as "a small ball of light" just as well, replicates to clients
     * automatically with zero new renderer/model code (same "reuse vanilla pieces" discipline this
     * class's doc already committed to), and is the standard vanilla idiom for this exact look (e.g.
     * a thrown Ender Pearl's own purple trail).
     */
    // Real follow-up request, 2026-10-20: "can you color it green?" -- confirmed against the real
    // decompiled DustParticleOptions class (not guessed) that its constructor is (packed RGB int,
    // scale), not a Vector3f directly. A plain nature green.
    private static final DustParticleOptions TRAIL_PARTICLE = new DustParticleOptions(0x00FF00, 1.0F);

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel serverLevel && !isRemoved()) {
            serverLevel.sendParticles(TRAIL_PARTICLE, getX(), getY(), getZ(), 1, 0.0, 0.0, 0.0, 0.0);
        }
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
                // Spell Proficiency (per-spell cost/cooldown reduction, AND -- as of 2026-10-21 --
                // the sole source of Magician's own level, see MagicianListener's class doc) is
                // heal/overheal-only -- explicit user spec: "Spark of Life Proficiency gain when
                // specifically hitting Spark of Life on a target and that target actually being
                // healed/overhealed."
                MagicianListener.grantProgress(player, SpellIds.SPARK_OF_LIFE, SpellProficiencyCurve.PROGRESS_PER_CAST);
            }
        } else {
            double velocity = getDeltaMovement().length();
            int damage = (int) Math.ceil(velocity * BASE_DAMAGE);
            DamageSource damageSource = damageSources().indirectMagic(this, owner);
            // Damaging an enemy grants nothing toward Spell Proficiency (and therefore nothing
            // toward Magician either, since Magician's level is now purely the sum of Proficiency
            // levels) -- explicit user spec, see the heal branch above. hurtOrSimulate is still used
            // (not a plain hurt) purely for the "did this actually do anything" semantics, consistent
            // with the rest of this class, even though nothing currently reads its result here.
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
