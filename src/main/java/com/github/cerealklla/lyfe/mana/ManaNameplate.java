package com.github.cerealklla.lyfe.mana;

import java.util.Optional;

import com.github.cerealklla.lyfe.registration.ModAttachments;
import com.github.cerealklla.lyfe.registration.ModAttributes;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;

/**
 * A nameplate-ready Mana indicator line for any {@link LivingEntity} (design doc, 2026-10-20: "...
 * above NPC heads when not at 100%"). Deliberately reuses Settlemynts' own {@code
 * npc.NpcHealthBar} technique -- a plain colored block-character bar via vanilla's real nametag
 * rendering, not a new world-space icon renderer -- per the user's own explicit choice when asked
 * ("Reuse the nameplate-text bar (Recommended)"). Exposed via {@code api.Lyfe#manaNameplateLine}
 * so Settlemynts (which already depends on Lyfe, one-way) can append this to its existing
 * health-bar nameplate build rather than this class writing {@code setCustomName} itself --
 * two independent listeners both calling {@code setCustomName} on the same entity would race and
 * stomp each other's text.
 */
public final class ManaNameplate {

    private static final int SEGMENTS = 10;
    private static final char FILLED = '█';
    private static final char EMPTY = '░';

    private ManaNameplate() {
    }

    public static Optional<Component> lineFor(LivingEntity entity) {
        AttributeInstance maxManaAttribute = entity.getAttribute(ModAttributes.MAX_MANA);
        if (maxManaAttribute == null) {
            return Optional.empty();
        }
        double max = maxManaAttribute.getValue();
        double current = entity.getData(ModAttachments.ENTITY_MANA).getCurrentMana();
        if (max <= 0.0 || current >= max) {
            return Optional.empty();
        }

        double fraction = Math.max(0.0, current / max);
        int filled = (int) Math.round(fraction * SEGMENTS);
        StringBuilder bar = new StringBuilder(SEGMENTS);
        for (int i = 0; i < SEGMENTS; i++) {
            bar.append(i < filled ? FILLED : EMPTY);
        }
        return Optional.of(Component.literal(bar.toString()).withStyle(ChatFormatting.AQUA));
    }
}
