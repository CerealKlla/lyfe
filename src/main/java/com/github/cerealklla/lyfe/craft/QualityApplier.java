package com.github.cerealklla.lyfe.craft;

import com.github.cerealklla.lyfe.registration.ModItems;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Applies a Quality score to a freshly-crafted item stack (design doc Section 19.2.1): the 1:1
 * durability bonus and the named/colored display -- never applied to non-durability items.
 */
final class QualityApplier {

    private QualityApplier() {
    }

    static void apply(ItemStack stack, double qualityScore) {
        if (!stack.isDamageableItem()) {
            return;
        }
        stack.set(ModItems.QUALITY.get(), (float) qualityScore);

        int baseMaxDamage = stack.getMaxDamage();
        int boostedMaxDamage = (int) Math.round(baseMaxDamage * (1.0 + qualityScore / 100.0));
        stack.set(DataComponents.MAX_DAMAGE, boostedMaxDamage);

        QualityTier tier = QualityTier.forScore(qualityScore);
        Component name = Component.literal(tier.name().charAt(0) + tier.name().substring(1).toLowerCase(java.util.Locale.ROOT) + " ")
                .append(stack.getHoverName())
                .withStyle(style -> style.withColor(tier.color()));
        stack.set(DataComponents.CUSTOM_NAME, name);
    }
}
