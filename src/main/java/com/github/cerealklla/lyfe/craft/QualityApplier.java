package com.github.cerealklla.lyfe.craft;

import com.github.cerealklla.lyfe.registration.ModItems;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Applies a Quality score to a freshly-crafted item stack (design doc Section 19.2.1): the 1:1
 * durability bonus (damageable items only) and the named/colored display.
 *
 * <p><b>Real report, 2026-10-24: "I thought we were supposed to be applying quality to Focuses, but
 * it's still not working"</b> -- Quality's stat-roll effect on a Focus (see {@code
 * magician.FocusStatRoller#rollStats}, called from {@code CraftingStructureMenu#attemptCraft}) was
 * always real, but {@link #apply} used to bail out entirely for any non-damageable item (Focus is
 * explicitly, deliberately never damageable -- see {@code magician.FocusItem}'s own doc), so the
 * *visible* half -- the `QUALITY` component and the named/colored prefix -- never showed up at all,
 * making the whole mechanic look broken even though it wasn't. {@link #apply} now only skips the
 * durability math for a non-damageable item; the display half (now {@link #applyDisplay}, called by
 * both {@link #apply} and directly by `CraftingStructureMenu`'s Focus branch) always runs. Not
 * widened into a blanket "every crafted item gets a name/Quality component regardless of
 * damageability" change -- that would also catch `MiscRecipes`' raw-material conversions (Charcoal,
 * Flint, Cobblestone), which should never get a quality-tier name; Focus opts in explicitly instead.
 */
final class QualityApplier {

    private QualityApplier() {
    }

    static void apply(ItemStack stack, double qualityScore) {
        // Deliberately still skips non-damageable items entirely (not just the durability math) --
        // this is the generic call site every crafted result goes through, including MiscRecipes'
        // raw-material conversions (Charcoal, Flint, Cobblestone), which must never get a quality-
        // tier name. Focus opts into the display half explicitly instead -- see applyDisplay.
        if (!stack.isDamageableItem()) {
            return;
        }
        int baseMaxDamage = stack.getMaxDamage();
        int boostedMaxDamage = (int) Math.round(baseMaxDamage * (1.0 + qualityScore / 100.0));
        stack.set(DataComponents.MAX_DAMAGE, boostedMaxDamage);
        applyDisplay(stack, qualityScore);
    }

    /** The `QUALITY` component + named/colored prefix only, no durability math -- see class doc for why Focus needs this called directly. */
    static void applyDisplay(ItemStack stack, double qualityScore) {
        stack.set(ModItems.QUALITY.get(), (float) qualityScore);

        QualityTier tier = QualityTier.forScore(qualityScore);
        Component name = Component.literal(tier.name().charAt(0) + tier.name().substring(1).toLowerCase(java.util.Locale.ROOT) + " ")
                .append(stack.getHoverName())
                .withStyle(style -> style.withColor(tier.color()));
        stack.set(DataComponents.CUSTOM_NAME, name);
    }
}
