package com.github.cerealklla.lyfe.cook;

import java.util.Locale;

import com.github.cerealklla.lyfe.skill.Skills;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;

/**
 * Cook XP + icon baking (design doc Section 19.3, reworked 2026-10-03) -- not an event listener on
 * eating (that mechanic is retired, see {@code hunger.HungerListener}'s class doc); this logic is
 * called directly from {@code CookingStructureMenu}'s "Cook" button at craft time.
 */
public final class CookingListener {

    private static final long COOK_XP_BASE_PER_CRAFT = 5L;
    private static final long COOK_XP_PER_ICON = 2L;

    private CookingListener() {
    }

    /**
     * {@code icons = structureTier + 0.5*floor(cookLevel/5) + 0.25*componentsUsed}, taken literally
     * (2026-10-03, confirmed with the user -- the original spec's own "18.5" worked example was a
     * typo for 18.75; no reserved-slot adjustment).
     */
    public static double computeIcons(int structureTier, int cookLevel, int componentsUsed) {
        return structureTier + 0.5 * Math.floor(cookLevel / 5.0) + 0.25 * componentsUsed;
    }

    /** Bakes {@code icons} into the result stack's own nutrition/name -- the only way a Crafted food item's icon count is ever set. */
    public static void bakeIcons(ItemStack result, double icons, int tier) {
        FoodProperties base = result.get(DataComponents.FOOD);
        int nutrition = Math.max(1, (int) Math.round(icons * 2.0));
        // Mirrors vanilla's own FoodConstants#saturationByModifier shape (saturation = nutrition *
        // modifier * 2) using a flat mid-range modifier, since a generated dish has no vanilla
        // saturation value of its own to preserve.
        float saturation = nutrition * 0.4F;
        result.set(DataComponents.FOOD, new FoodProperties(nutrition, saturation, base != null && base.canAlwaysEat()));
        String baseName = result.getHoverName().getString();
        result.set(DataComponents.CUSTOM_NAME, Component.literal(
                "[" + trimmed(icons) + "] (T" + tier + ") - " + baseName));
    }

    private static String trimmed(double icons) {
        if (icons == Math.floor(icons)) {
            return String.format(Locale.ROOT, "%.0f", icons);
        }
        return String.format(Locale.ROOT, "%.2f", icons).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    public static long xpForCraft(double icons) {
        return COOK_XP_BASE_PER_CRAFT + Math.round(icons * COOK_XP_PER_ICON);
    }

    /** Live benefit readout for the Skills screen (common.skill.SkillBenefits). */
    public static java.util.List<String> benefitLines(int level) {
        return java.util.List.of(
                "Icon bonus from level: +" + String.format(Locale.ROOT, "%.1f", 0.5 * Math.floor(level / 5.0)),
                "Max researchable recipe tier: " + CookConstants.researchUnlockedTier(level)
        );
    }
}
