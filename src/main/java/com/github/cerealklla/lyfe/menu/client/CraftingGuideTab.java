package com.github.cerealklla.lyfe.menu.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.github.cerealklla.lyfe.craft.EquipmentTierLadder;
import com.github.cerealklla.lyfe.craft.GeneratedRecipe;
import com.github.cerealklla.lyfe.craft.RequestCraftingRecipesPayload;
import com.github.cerealklla.lyfe.registration.ModAttachments;
import com.github.cerealklla.lyfe.research.PlayerResearch;
import com.github.cerealklla.nuclyus.menu.client.NuclyusMenu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.equipment.ArmorType;

/**
 * "Crafting Guide" tab -- every recipe the player currently knows, grouped into logical sections.
 * Needs no new network payload for the base list at all: {@code EquipmentTierLadder}'s id<->tier
 * mappings are pure, identical-on-both-sides static data already usable client-side, and {@code
 * PlayerResearch}'s {@code learnedRecipes} set (turned on sync 2026-10-17, see {@code
 * ModAttachments.PLAYER_RESEARCH}'s own comment for the real sync-bug fix that had to go with it)
 * is the only piece that was ever missing. Tier 0 (Wood tools, Leather armor) is always shown
 * known, matching the established "auto-known" convention {@code
 * CraftingStructureMenu#computeCraftable}/{@code ResearchMenu} both already use -- it never
 * actually enters {@code learnedRecipes}.
 *
 * <p>Real report, 2026-10-17: "the crafting guide needs tooltips for the recipes too" -- a row's
 * actual ingredient cost ({@link GeneratedRecipe}) is server-generated-per-server data, never
 * synced on its own, so showing it needs one small request/response round trip ({@link
 * RequestCraftingRecipesPayload}/{@code craft.CraftingRecipesPayload}), same shape as the Rune
 * Codex's roll request. Requested once the first time this tab is shown, cached in {@link #recipes}
 * thereafter (a static cache, same convention {@link RuneCodexTab#rolls} already uses).
 */
public final class CraftingGuideTab implements NuclyusMenu {

    private static final int MARGIN = 10;
    private static final int ROW_HEIGHT = 11;
    private static final int SECTION_GAP = 6;
    private static final int SECTION_HEADER_COLOR = 0xFFFFD966;
    private static final int ITEM_COLOR = 0xFF7FD17F;

    private static Map<Identifier, GeneratedRecipe> recipesByResultId = Map.of();
    private static boolean recipesRequested;

    /** Written by {@code LyfeMod}'s payload handler for {@code craft.CraftingRecipesPayload}. */
    public static void setRecipes(List<GeneratedRecipe> recipes) {
        Map<Identifier, GeneratedRecipe> byId = new HashMap<>();
        for (GeneratedRecipe recipe : recipes) {
            byId.put(recipe.resultId(), recipe);
        }
        recipesByResultId = byId;
    }

    private record Entry(String label, Identifier itemId) {
    }

    private record Section(String title, List<Entry> entries) {
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

        if (!recipesRequested) {
            recipesRequested = true;
            var connection = Minecraft.getInstance().getConnection();
            if (connection != null) {
                connection.send(new ServerboundCustomPayloadPacket(new RequestCraftingRecipesPayload()));
            }
        }
    }

    private Section buildToolSection(PlayerResearch research) {
        List<Entry> entries = new ArrayList<>();
        for (EquipmentTierLadder.ToolType tool : EquipmentTierLadder.ToolType.values()) {
            for (int tier = EquipmentTierLadder.MIN_TIER; tier <= EquipmentTierLadder.MAX_TIER; tier++) {
                Identifier itemId = EquipmentTierLadder.itemId(tool, tier);
                if (tier == 0 || research.isLearned(itemId)) {
                    entries.add(new Entry(EquipmentTierLadder.tierDisplayName(tier) + " " + capitalize(tool.name()), itemId));
                }
            }
        }
        return new Section("Tools & Weapons", entries);
    }

    private Section buildArmorSection(PlayerResearch research) {
        List<Entry> entries = new ArrayList<>();
        for (ArmorType armorType : EquipmentTierLadder.ARMOR_TYPES) {
            for (int tier = EquipmentTierLadder.MIN_TIER; tier <= EquipmentTierLadder.MAX_TIER; tier++) {
                Identifier itemId = EquipmentTierLadder.armorItemId(armorType, tier);
                if (tier == 0 || research.isLearned(itemId)) {
                    entries.add(new Entry(EquipmentTierLadder.tierDisplayName(tier) + " " + capitalize(armorType.getName()), itemId));
                }
            }
        }
        return new Section("Armor", entries);
    }

    private Section buildRangedSection(PlayerResearch research) {
        List<Entry> entries = new ArrayList<>();
        for (Identifier itemId : EquipmentTierLadder.allGeneratedRangedWeaponIds()) {
            if (research.isLearned(itemId)) {
                entries.add(new Entry(capitalize(itemId.getPath()) + " (Tier " + EquipmentTierLadder.rangedWeaponTier(itemId) + ")", itemId));
            }
        }
        return new Section("Ranged Weapons", entries);
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
            height += ROW_HEIGHT + section.entries().size() * ROW_HEIGHT + SECTION_GAP;
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
        Identifier hoveredItemId = null;
        for (Section section : sections) {
            if (y + ROW_HEIGHT >= top && y <= bottom) {
                graphics.text(font, Component.literal(section.title()), left, y, SECTION_HEADER_COLOR, false);
            }
            y += ROW_HEIGHT;
            if (section.entries().isEmpty()) {
                if (y + ROW_HEIGHT >= top && y <= bottom) {
                    graphics.text(font, Component.literal("(none known)"), left + MARGIN, y, ITEM_COLOR, false);
                }
                y += ROW_HEIGHT;
            } else {
                for (Entry entry : section.entries()) {
                    if (y + ROW_HEIGHT >= top && y <= bottom) {
                        graphics.text(font, Component.literal(entry.label()), left + MARGIN, y, ITEM_COLOR, false);
                        if (mouseX >= left && mouseX < right && mouseY >= y && mouseY < y + ROW_HEIGHT) {
                            hoveredItemId = entry.itemId();
                        }
                    }
                    y += ROW_HEIGHT;
                }
            }
            y += SECTION_GAP;
        }
        graphics.disableScissor();

        if (hoveredItemId != null) {
            GeneratedRecipe recipe = recipesByResultId.get(hoveredItemId);
            if (recipe != null) {
                graphics.setComponentTooltipForNextFrame(font, recipeTooltip(recipe), mouseX, mouseY);
            }
        }
    }

    /** "Cost:\n2x Iron Ingot\n1x Any Log" -- same ingredient formatting {@code craft.client.CraftingStructureScreen}'s cost panel already uses, just as tooltip lines instead of a fixed panel. */
    private static List<Component> recipeTooltip(GeneratedRecipe recipe) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("Cost:"));
        for (Map.Entry<Identifier, Integer> entry : recipe.specificComponents().entrySet()) {
            lines.add(Component.literal(entry.getValue() + "x " + itemDisplayName(entry.getKey())));
        }
        for (Map.Entry<String, Integer> entry : recipe.genericComponents().entrySet()) {
            lines.add(Component.literal(entry.getValue() + "x " + entry.getKey()));
        }
        return lines;
    }

    private static String itemDisplayName(Identifier itemId) {
        var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(itemId);
        return new net.minecraft.world.item.ItemStack(item).getHoverName().getString();
    }
}
