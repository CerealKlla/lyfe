package com.github.cerealklla.lyfe.craft.client;

import com.github.cerealklla.lyfe.api.Lyfe;
import com.github.cerealklla.lyfe.craft.EquipmentTierLadder;
import com.github.cerealklla.lyfe.craft.ToolTierUnlocks;
import com.github.cerealklla.lyfe.skill.Skills;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.ArmorType;
import net.neoforged.neoforge.client.gui.GuiLayer;

/**
 * Persistent red-caution HUD line shown while holding/wearing gear above the player's unlocked
 * skill tier (2026-10-06, user request, reworked same day second round once the enforcement
 * mechanic changed from a hard block to an extra-durability-damage penalty, see {@code
 * ProficiencyDurabilityListener}) -- same above-hotbar placement/box styling as Settlemynts'
 * {@code zone.client.PlotValidityOverlay} (that mod's own "needs a 15x15 buildable area" warning),
 * the reference point the user named, reproduced here rather than shared across mods since
 * Settlemynts has no compile dependency on Lyfe.
 *
 * <p>Advisory only, for both tool/weapon (mainhand) and armor now -- nothing is blocked any more,
 * this just names the actual consequence. Exact wording is the user's own: "Not proficient in
 * {@literal <tier>}, excessive durability damage."
 */
public final class EquipmentSkillWarningOverlay implements GuiLayer {

    private static final int MARGIN_FROM_BOTTOM = 52; // matches PlotValidityOverlay's own placement
    private static final int PADDING = 4;
    private static final int WARNING_COLOR = 0xFFFF5555;
    private static final int BACKGROUND_COLOR = 0xE0101010;

    @Override
    public void render(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null) {
            return;
        }
        String text = computeWarning(player);
        if (text == null) {
            return;
        }

        Font font = minecraft.font;
        int textWidth = font.width(text);
        int left = guiGraphics.guiWidth() / 2 - textWidth / 2 - PADDING;
        int right = left + textWidth + PADDING * 2;
        int bottom = guiGraphics.guiHeight() - MARGIN_FROM_BOTTOM;
        int top = bottom - font.lineHeight - PADDING * 2;

        guiGraphics.fill(left, top, right, bottom, BACKGROUND_COLOR);
        guiGraphics.text(font, text, left + PADDING, top + PADDING, WARNING_COLOR);
    }

    private static String computeWarning(Player player) {
        ItemStack mainHand = player.getMainHandItem();
        for (EquipmentTierLadder.ToolType tool : EquipmentTierLadder.ToolType.values()) {
            int tier = EquipmentTierLadder.tierOfItem(tool, mainHand.getItem());
            if (tier < 0) {
                continue;
            }
            int level = Lyfe.getLevel(player, EquipmentTierLadder.governingSkillId(tool));
            String warning = proficiencyWarning(tier, level);
            if (warning != null) {
                return warning;
            }
        }
        int crafterLevel = Lyfe.getLevel(player, Skills.CRAFTER_ID);
        for (ArmorType armorType : EquipmentTierLadder.ARMOR_TYPES) {
            ItemStack piece = player.getItemBySlot(armorType.getSlot());
            if (piece.isEmpty()) {
                continue;
            }
            int tier = EquipmentTierLadder.tierOfArmorItem(armorType, piece.getItem());
            if (tier < 0) {
                continue;
            }
            String warning = proficiencyWarning(tier, crafterLevel);
            if (warning != null) {
                return warning;
            }
        }
        return null;
    }

    /** {@code null} unless {@code tier} actually costs extra durability at {@code level}. */
    private static String proficiencyWarning(int tier, int level) {
        if (ToolTierUnlocks.extraDamageMultiplier(tier, ToolTierUnlocks.unlockedTier(level)) <= 0) {
            return null;
        }
        return "⚠ Not proficient in " + EquipmentTierLadder.tierDisplayName(tier) + ", excessive durability damage";
    }
}
