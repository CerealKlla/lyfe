package com.github.cerealklla.lyfe.craft;

import net.minecraft.ChatFormatting;

/**
 * The Quality named-tier ladder (design doc Section 19.2.1) -- bucket names/ranges/colors are this
 * mod's own first-pass judgment call (the user explicitly left this to us), not a direct quote.
 */
public enum QualityTier {
    POOR(0, 20, ChatFormatting.GRAY),
    COMMON(20, 40, ChatFormatting.WHITE),
    FINE(40, 60, ChatFormatting.GREEN),
    SUPERIOR(60, 80, ChatFormatting.BLUE),
    EXCEPTIONAL(80, 100, ChatFormatting.LIGHT_PURPLE),
    MASTERWORK(100, 110, ChatFormatting.GOLD),
    LEGENDARY(110, 120, ChatFormatting.RED),
    MYTHICAL(120, Integer.MAX_VALUE, ChatFormatting.DARK_PURPLE);

    private final double minInclusive;
    private final double maxExclusive;
    private final ChatFormatting color;

    QualityTier(double minInclusive, double maxExclusive, ChatFormatting color) {
        this.minInclusive = minInclusive;
        this.maxExclusive = maxExclusive;
        this.color = color;
    }

    public ChatFormatting color() {
        return color;
    }

    public static QualityTier forScore(double score) {
        for (QualityTier tier : values()) {
            if (score >= tier.minInclusive && score < tier.maxExclusive) {
                return tier;
            }
        }
        return MYTHICAL;
    }
}
