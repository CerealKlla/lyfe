package com.github.cerealklla.lyfe.durability.mixin;

import java.util.function.Consumer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Global "tools and armor never actually break" mechanic (2026-10-06, user request). Every vanilla
 * break path (mining-tool break, melee-weapon break, armor break from taking damage) funnels through
 * the private {@code ItemStack#applyDamage(int, LivingEntity, Consumer<Item>)}, confirmed against the
 * decompiled source -- there is no event around it (see {@code craft.ProficiencyDurabilityListener}'s
 * own doc comment, which independently confirmed the same thing for a different feature). Vanilla
 * already has a sibling method, {@code ItemStack#hurtWithoutBreaking}, that does exactly this
 * clamp-and-never-break logic -- this mixin just makes it the ALWAYS behavior instead of an
 * opt-in alternate path.
 *
 * <p>Lyfe's own Research Bench ({@code research.ResearchMenu}) damages items via direct
 * {@code setDamageValue} calls, never {@code hurtAndBreak}/{@code applyDamage} -- so it's naturally,
 * structurally unaffected by this mixin, no exception logic needed.
 *
 * <p>This is Lyfe's first-ever Mixin, added only after confirming no event-based alternative covers
 * all three break paths (in particular, armor broken by taking damage has no covering event at all) --
 * see decisions.md for the full discussion.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackUnbreakableMixin {

    @Inject(
            method = "applyDamage(ILnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void lyfe$neverBreak(int newDamage, LivingEntity player, Consumer<Item> onBreak, CallbackInfo ci) {
        ItemStack self = (ItemStack) (Object) this;
        int maxDamage = self.getMaxDamage();
        if (maxDamage <= 0) {
            return;
        }
        self.setDamageValue(Math.min(newDamage, maxDamage - 1));
        ci.cancel();
    }
}
