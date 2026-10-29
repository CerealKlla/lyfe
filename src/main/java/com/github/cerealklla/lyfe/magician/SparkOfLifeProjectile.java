package com.github.cerealklla.lyfe.magician;

import com.github.cerealklla.lyfe.combat.TagEffectivenessTable;
import com.github.cerealklla.lyfe.registration.ModEntities;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
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
 * caster whenever both are hostile, or both are non-hostile, per {@link SpellTargeting}'s own
 * canonical check (the same shared hostility marker this suite's {@code guardhouse.GuardSightGoal}
 * already uses).
 */
public final class SparkOfLifeProjectile extends ThrowableItemProjectile {

    // Matches AbstractArrow's own default baseDamage (2.0) -- combined with this spell's launch
    // speed, reused by SparkOfLifeCasting (see its own doc) to compute a fixed per-cast damage
    // value, the same ballpark a real unenchanted bow deals at that Tier's own launch speed.
    public static final double BASE_DAMAGE = 2.0;

    // Real report, 2026-10-22: "spells should not lose damage as they fly, it's a spell not a
    // projectile." Previously recomputed from getDeltaMovement().length() at the moment of impact,
    // which vanilla's own drag/gravity physics decay over distance/flight time exactly like a real
    // arrow -- correct for a physical weapon, wrong for a spell. SparkOfLifeCasting now computes this
    // once, from the caster's Nature Staff Tier alone, at the instant of casting, and threads it
    // through via setDamage -- distance/travel time no longer affects it at all.
    private int fixedDamage;

    public SparkOfLifeProjectile(EntityType<SparkOfLifeProjectile> type, Level level) {
        super(type, level);
    }

    public SparkOfLifeProjectile(Level level, LivingEntity owner) {
        super(ModEntities.SPARK_OF_LIFE_PROJECTILE.get(), owner, level, new ItemStack(Items.GLOWSTONE_DUST));
    }

    /** Set once at cast time by {@code SparkOfLifeCasting} -- see {@link #fixedDamage}'s own doc for why this isn't derived from live flight velocity. */
    public void setDamage(int damage) {
        this.fixedDamage = damage;
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

    /**
     * Real root cause of a live server performance incident, 2026-10-21: this class only ever
     * discarded itself on a hit *entity* (see {@link #onHitEntity}) -- the base {@code
     * Projectile#onHitBlock} only dispatches {@code BlockState#onProjectileHit}, it never discards
     * on its own (confirmed against the decompiled source; vanilla's own throwables, e.g. {@code
     * Snowball},
     * override the shared {@code onHit} dispatcher specifically to guarantee a discard on *either*
     * hit type). Any cast that lands on terrain instead of a living target -- the common case for
     * guards auto-casting at range -- never got removed, ticking (and sending a particle) forever;
     * 146 live instances had accumulated after about 9 minutes of uptime with only 4 casting guards,
     * measurably degrading tick time server-wide with no correlation to player location at all.
     */
    @Override
    protected void onHitBlock(BlockHitResult hitResult) {
        super.onHitBlock(hitResult);
        if (!level().isClientSide()) {
            discard();
        }
    }

    /**
     * Iterates {@link Spells#SPARK_OF_LIFE}'s effects (design doc Section 24's generic Spell/
     * SpellEffect model, replacing this class's original hardcoded ally/enemy + heal/damage
     * branches one-for-one) -- applies whichever effect's {@link SpellEffect.TargetList} matches
     * the caster/target relationship AND isn't tag-blocked for this target (e.g. a non-organic
     * entity blocks {@code heal_organic}/{@code damage_organic}). If nothing applied at all and the
     * caster is a real player, sends chat feedback naming whichever effect would have applied by
     * relationship but got tag-blocked (e.g. "Wagon cannot be healed.").
     */
    @Override
    protected void onHitEntity(EntityHitResult hitResult) {
        if (!(hitResult.getEntity() instanceof LivingEntity target) || !(getOwner() instanceof LivingEntity caster)) {
            discard();
            return;
        }

        boolean appliedAnything = false;
        SpellEffect blockedEffect = null;
        for (SpellEffect effect : Spells.SPARK_OF_LIFE.effects()) {
            if (!SpellTargeting.matches(effect.targetList(), caster, target)) {
                continue;
            }
            if (TagEffectivenessTable.multiplierFor(effect.combinedTags(), target) <= 0.0F) {
                blockedEffect = effect;
                continue;
            }
            boolean effective = effect.apply(caster, target, fixedDamage);
            if (effective) {
                appliedAnything = true;
                // Spell Proficiency (per-spell cost/cooldown reduction, AND -- as of 2026-10-21 --
                // the sole source of Magician's own level, see MagicianListener's class doc) is
                // heal/overheal-only -- explicit user spec: "Spark of Life Proficiency gain when
                // specifically hitting Spark of Life on a target and that target actually being
                // healed/overhealed." Damaging an enemy grants nothing toward it.
                if (effect == SpellEffects.HEAL_ORGANIC_ALLY_LIFE && caster instanceof ServerPlayer player) {
                    MagicianListener.grantProgress(player, SpellIds.SPARK_OF_LIFE, SpellProficiencyCurve.PROGRESS_PER_CAST);
                }
            }
        }

        if (!appliedAnything && blockedEffect != null && caster instanceof ServerPlayer player) {
            player.sendSystemMessage(Component.literal(
                    target.getDisplayName().getString() + " cannot be " + blockedEffect.feedbackVerb() + "."));
        }
        discard();
    }
}
