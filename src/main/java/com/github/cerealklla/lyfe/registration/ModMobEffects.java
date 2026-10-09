package com.github.cerealklla.lyfe.registration;

import com.github.cerealklla.lyfe.LyfeMod;
import com.github.cerealklla.lyfe.heartiness.RapidRecoveryMobEffect;
import com.github.cerealklla.lyfe.reincarnation.SummoningSicknessMobEffect;
import com.github.cerealklla.lyfe.rest.WellRestedMobEffect;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Both custom status effects added 2026-10-03 (Heartiness, the bed-rest replacement) -- built on
 * the real vanilla {@code MobEffect}/{@code MobEffectInstance} framework per the user's explicit
 * instruction to reuse that system rather than invent a new one, same as every other built-in
 * status effect (Regeneration, Hunger, etc.).
 */
public final class ModMobEffects {

    private ModMobEffects() {
    }

    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, LyfeMod.MODID);

    public static final DeferredHolder<MobEffect, RapidRecoveryMobEffect> RAPID_RECOVERY = MOB_EFFECTS.register(
            "rapid_recovery",
            () -> new RapidRecoveryMobEffect(MobEffectCategory.BENEFICIAL, 0xFF4040));

    public static final DeferredHolder<MobEffect, WellRestedMobEffect> WELL_RESTED = MOB_EFFECTS.register(
            "well_rested",
            () -> new WellRestedMobEffect(MobEffectCategory.BENEFICIAL, 0x7EC8E3));

    public static final DeferredHolder<MobEffect, SummoningSicknessMobEffect> SUMMONING_SICKNESS = MOB_EFFECTS.register(
            "summoning_sickness",
            () -> new SummoningSicknessMobEffect(MobEffectCategory.HARMFUL, 0x4B2E5A));
}
