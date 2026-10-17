package com.github.cerealklla.lyfe.menu.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.github.cerealklla.lyfe.craft.EquipmentTierLadder;
import com.github.cerealklla.lyfe.registration.ModAttachments;
import com.github.cerealklla.lyfe.research.PlayerResearch;
import com.github.cerealklla.nuclyus.menu.client.NuclyusMenu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.equipment.ArmorType;

/**
 * "Crafting Guide" tab -- every recipe the player currently knows, grouped into logical sections.
 * Needs no new network payload at all: {@code EquipmentTierLadder}'s id<->tier mappings are pure,
 * identical-on-both-sides static data already usable client-side, and {@code PlayerResearch}'s
 * {@code learnedRecipes} set (turned on sync 2026-10-17, see {@code ModAttachments.PLAYER_RESEARCH}'s
 * own comment for the real sync-bug fix that had to go with it) is the only piece that was ever
 * missing. Tier 0 (Wood tools, Leather armor) is always shown known, matching the established
 * "auto-known" convention {@code CraftingStructureMenu#computeCraftable}/{@code ResearchMenu} both
 * already use -- it never actually enters {@code learnedRecipes}.
 */
public final class CraftingGuideTab implements NuclyusMenu {

    private static final int MARGIN = 10;
    private static final int ROW_HEIGHT = 11;
    private static final int SECTION_GAP = 6;
    private static final int SECTION_HEADER_COLOR = 0xFFFFD966;
    private static final int ITEM_COLOR = 0xFF7FD17F;

    private record Section(String title, List<String> items) {
    }

    private List<Section> sections = List.of();
    private int scrollOffset;

    @Override
    public Component title() {
        return Component.literal("Crafting Guide");
    }

    @Override
    public int order() {
        return 6;
    }

    @Override
    public void onShown() {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            sections = List.of();
            return;
        }
        PlayerResearch research = player.getData(ModAttachments.PLAYER_RESEARCH);
        sections = List.of(buildToolSection(research), buildArmorSection(research), buildRangedSection(research));
        scrollOffset = 0;
    }

    private Section buildToolSection(PlayerResearch research) {
        List<String> items = new ArrayList<>();
        for (EquipmentTierLadder.ToolType tool : EquipmentTierLadder.ToolType.values()) {
            for (int tier = EquipmentTierLadder.MIN_TIER; tier <= EquipmentTierLadder.MAX_TIER; tier++) {
                Identifier itemId = EquipmentTierLadder.itemId(tool, tier);
                if (tier == 0 || research.isLearned(itemId)) {
                    items.add(EquipmentTierLadder.tierDisplayName(tier) + " " + capitalize(tool.name()));
                }
            }
        }
        return new Section("Tools & Weapons", items);
    }

    private Section buildArmorSection(PlayerResearch research) {
        List<String> items = new ArrayList<>();
        for (ArmorType armorType : EquipmentTierLadder.ARMOR_TYPES) {
            for (int tier = EquipmentTierLadder.MIN_TIER; tier <= EquipmentTierLadder.MAX_TIER; tier++) {
                Identifier itemId = EquipmentTierLadder.armorItemId(armorType, tier);
                if (tier == 0 || research.isLearned(itemId)) {
                    items.add(EquipmentTierLadder.tierDisplayName(tier) + " " + capitalize(armorType.getName()));
                }
            }
        }
        return new Section("Armor", items);
    }

    private Section buildRangedSection(PlayerResearch research) {
        List<String> items = new ArrayList<>();
        for (Identifier itemId : EquipmentTierLadder.allGeneratedRangedWeaponIds()) {
            if (research.isLearned(itemId)) {
                items.add(capitalize(itemId.getPath()) + " (Tier " + EquipmentTierLadder.rangedWeaponTier(itemId) + ")");
            }
        }
        return new Section("Ranged Weapons", items);
    }

    private static String capitalize(String text) {
        String[] parts = text.toLowerCase(Locale.ROOT).split("_");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(' ');
            }
        }
        return result.toString().trim();
    }

    @Override
    public void onHidden() {
    }

    private int contentHeight() {
        int height = 0;
        for (Section section : sections) {
            height += ROW_HEIGHT + section.items().size() * ROW_HEIGHT + SECTION_GAP;
        }
        return height;
    }

    @Override
    public boolean mouseScrolled(Rect2i area, double mouseX, double mouseY, double scrollX, double scrollY) {
        int viewport = area.getHeight() - 2 * MARGIN;
        int maxScroll = Math.max(0, contentHeight() - viewport);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) (scrollY * ROW_HEIGHT * 2)));
        return true;
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, Rect2i area, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        int left = area.getX() + MARGIN;
        int top = area.getY() + MARGIN;
        int bottom = area.getY() + area.getHeight() - MARGIN;
        int right = area.getX() + area.getWidth() - MARGIN;

        graphics.enableScissor(left, top, right, bottom);
        int y = top - scrollOffset;
        for (Section section : sections) {
            if (y + ROW_HEIGHT >= top && y <= bottom) {
                graphics.text(font, Component.literal(section.title()), left, y, SECTION_HEADER_COLOR, false);
            }
            y += ROW_HEIGHT;
            if (section.items().isEmpty()) {
                if (y + ROW_HEIGHT >= top && y <= bottom) {
                    graphics.text(font, Component.literal("(none known)"), left + MARGIN, y, ITEM_COLOR, false);
                }
                y += ROW_HEIGHT;
            } else {
                for (String item : section.items()) {
                    if (y + ROW_HEIGHT >= top && y <= bottom) {
                        graphics.text(font, Component.literal(item), left + MARGIN, y, ITEM_COLOR, false);
                    }
                    y += ROW_HEIGHT;
                }
            }
            y += SECTION_GAP;
        }
        graphics.disableScissor();
    }
}
