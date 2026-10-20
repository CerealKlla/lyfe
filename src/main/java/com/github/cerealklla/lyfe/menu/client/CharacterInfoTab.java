package com.github.cerealklla.lyfe.menu.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.github.cerealklla.lyfe.api.Lyfe;
import com.github.cerealklla.lyfe.hunger.HungerListener;
import com.github.cerealklla.lyfe.registration.ModAttributes;
import com.github.cerealklla.lyfe.skill.Skills;
import com.github.cerealklla.lyfe.swim.SwimmerListener;
import com.github.cerealklla.nuclyus.menu.client.NuclyusMenu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.common.NeoForgeMod;

/**
 * "Character Info" tab (design doc: "all player stats... grouped into logical sections... each
 * stat [with] a tooltip with a brief description"). Pure client read, no server round trip --
 * every value here is either a real vanilla {@code Attribute} (always synced, and already
 * aggregates any Heartiness/gem/rune bonuses automatically) or one of Lyfe's own decoupled
 * "true value" systems, read via the same pure level-only formulas the Skills screen's {@code
 * SkillBenefits} dispatcher already uses (max hunger has no vanilla attribute backing it at all --
 * {@code hunger.HungerListener}/{@code swim.SwimmerListener} decouple it from vanilla's own hunger
 * system on purpose, see those classes' own docs).
 */
public final class CharacterInfoTab implements NuclyusMenu {

    private static final int MARGIN = 10;
    private static final int SECTION_GAP = 6;
    private static final int ROW_HEIGHT = 12;
    private static final int SECTION_HEADER_COLOR = 0xFFFFD966;
    private static final int STAT_NAME_COLOR = 0xFFE0E0E0;
    private static final int STAT_VALUE_COLOR = 0xFF7FD17F;

    private record Stat(String name, String value, Component tooltip) {
    }

    private record Section(String title, List<Stat> stats) {
    }

    private List<Section> sections = List.of();

    @Override
    public Component title() {
        return Component.literal("Character Info");
    }

    @Override
    public int order() {
        return 0;
    }

    @Override
    public void onShown() {
        sections = buildSections();
    }

    @Override
    public void onHidden() {
    }

    private List<Section> buildSections() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return List.of();
        }

        double maxHealth = attributeValue(player, Attributes.MAX_HEALTH);
        double maxAbsorption = attributeValue(player, Attributes.MAX_ABSORPTION);
        double swimSpeedBonus = attributeValue(player, NeoForgeMod.SWIM_SPEED);
        int survivalistLevel = Lyfe.getLevel(player, Skills.SURVIVALIST_ID);
        int swimmerLevel = Lyfe.getLevel(player, Skills.SWIMMER_ID);
        int maxHunger = HungerListener.currentMaxHunger(survivalistLevel);
        int maxAirTicks = SwimmerListener.currentMaxAir(swimmerLevel);

        Section vitality = new Section("Vitality", List.of(
                new Stat("Max Health", formatHearts(maxHealth),
                        Component.literal("The most health you can have, shown in hearts. Grows with your Heartiness skill and any equipped gem/rune bonuses.")),
                new Stat("Absorption", formatHearts(maxAbsorption),
                        Component.literal("The highest amount of extra 'shield' hearts you can have at once, on top of your normal health. Not an amount you currently have -- something still has to grant it, like a potion or totem."))));

        Section survival = new Section("Survival", List.of(
                new Stat("Food Capacity", maxHunger + " points",
                        Component.literal("The most hunger you can store before you're full. Grows with your Survivalist skill."))));

        Section aquatic = new Section("Aquatic", List.of(
                new Stat("Max Oxygen", String.format(Locale.ROOT, "%.1f seconds", maxAirTicks / 20.0),
                        Component.literal("How long you can hold your breath underwater before taking damage. Grows with your Swimmer skill.")),
                new Stat("Swim Speed", String.format(Locale.ROOT, "+%.2f", swimSpeedBonus),
                        Component.literal("How much faster you move while swimming, on top of the vanilla base speed. Grows with your Swimmer skill."))));

        // Real gap found live, 2026-10-20: this section was never added when Mana shipped at all --
        // every one of these four stats is already a real synced Attribute (registration.ModAttributes),
        // same read-only "live attribute value" pattern every other section on this tab already uses.
        double maxMana = attributeValue(player, ModAttributes.MAX_MANA);
        double manaRegen = attributeValue(player, ModAttributes.MANA_REGEN);
        double magicFind = attributeValue(player, ModAttributes.MAGIC_FIND);
        double goldFind = attributeValue(player, ModAttributes.GOLD_FIND);
        Section magic = new Section("Magic", List.of(
                new Stat("Max Mana", String.format(Locale.ROOT, "%.0f", maxMana),
                        Component.literal("The most Mana you can have. Grows with your Magician skill, your Nature Staff's tier/Quality, and any equipped Rune bonuses.")),
                new Stat("Mana Regen", String.format(Locale.ROOT, "+%.0f per second", manaRegen),
                        Component.literal("How quickly Mana replenishes. Grows with your Magician skill, your Nature Staff's tier/Quality, and any equipped Rune bonuses.")),
                new Stat("Magic Find", String.format(Locale.ROOT, "+%.0f%%", magicFind),
                        Component.literal("Increases your odds of finding Runes and Gems.")),
                new Stat("Gold Find", String.format(Locale.ROOT, "+%.0f%%", goldFind),
                        Component.literal("Increases the gold dropped by mobs you kill."))));

        return List.of(vitality, survival, aquatic, magic);
    }

    private static double attributeValue(LocalPlayer player, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute) {
        var instance = player.getAttribute(attribute);
        return instance == null ? 0.0 : instance.getValue();
    }

    /** Vanilla health/absorption attributes are in half-heart points -- divide by 2 for whole hearts. */
    private static String formatHearts(double points) {
        double hearts = points / 2.0;
        return (hearts == Math.floor(hearts))
                ? String.format(Locale.ROOT, "%.0f hearts", hearts)
                : String.format(Locale.ROOT, "%.1f hearts", hearts);
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, Rect2i area, int mouseX, int mouseY, float partialTick) {
        int left = area.getX() + MARGIN;
        int y = area.getY() + MARGIN;
        Component hoveredTooltip = null;

        for (Section section : sections) {
            graphics.text(Minecraft.getInstance().font, Component.literal(section.title()), left, y, SECTION_HEADER_COLOR, false);
            y += ROW_HEIGHT;
            for (Stat stat : section.stats()) {
                String line = stat.name() + ": ";
                graphics.text(Minecraft.getInstance().font, Component.literal(line), left + MARGIN, y, STAT_NAME_COLOR, false);
                int valueX = left + MARGIN + Minecraft.getInstance().font.width(line);
                graphics.text(Minecraft.getInstance().font, Component.literal(stat.value()), valueX, y, STAT_VALUE_COLOR, false);

                int rowWidth = MARGIN + Minecraft.getInstance().font.width(line) + Minecraft.getInstance().font.width(stat.value());
                if (mouseX >= left && mouseX < left + rowWidth && mouseY >= y && mouseY < y + ROW_HEIGHT) {
                    hoveredTooltip = stat.tooltip();
                }
                y += ROW_HEIGHT;
            }
            y += SECTION_GAP;
        }

        if (hoveredTooltip != null) {
            graphics.setTooltipForNextFrame(Minecraft.getInstance().font, hoveredTooltip, mouseX, mouseY);
        }
    }
}
