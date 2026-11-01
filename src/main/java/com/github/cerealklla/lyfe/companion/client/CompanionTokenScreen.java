package com.github.cerealklla.lyfe.companion.client;

import java.util.Locale;
import java.util.UUID;

import com.github.cerealklla.lyfe.companion.CompanionTokenMenu;
import com.github.cerealklla.lyfe.companion.RenameCompanionTokenPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;

/**
 * The Companion Token's Equip GUI real screen -- same "beveled flat panel, no texture file" visual
 * style as {@code summoning.client.AllayBottleScreen}, which this directly mirrors (6 equipment
 * slots in a row instead of an equip/gather pair; a live Health/Attack readout instead of
 * Mana/Mana Regen, since a Tavern Companion has no mana of its own; a rename {@link EditBox}/button
 * baked directly into the screen, same idiom as Allay's own "Rename" -- real user request,
 * 2026-11-01, "I need to be able to rename these people").
 *
 * <p><b>Overlap fix, 2026-11-01</b> (real report: "This UI is overlapping everywhere") -- widened to
 * fit six slot labels without bleeding into each other, and the panel's vertical rows (title, rename,
 * stats, slot labels, slots, inventory) now each get their own real gap, mirroring the layout
 * {@code AllayBottleScreen} was already fixed to use on 2026-10-24.
 *
 * <p><b>Mount slot, same day</b> -- "add 1 more slot on the companion where you are allowed to equip
 * a summon stone, which the companion can use to mount and ride around." A 7th slot on its own row
 * below the 6 equipment slots, see {@link CompanionTokenMenu#MOUNT_SLOT_X}'s own doc. The actual
 * summon-and-mount behavior is driven by {@code CompanionMountTicker}, not this screen.
 */
public final class CompanionTokenScreen extends AbstractContainerScreen<CompanionTokenMenu> {

    private static final int PANEL_BODY_COLOR = 0xFFC6C6C6;
    private static final int PANEL_OUTER_BORDER_COLOR = 0xFF373737;
    private static final int PANEL_HIGHLIGHT_COLOR = 0xFFFFFFFF;
    private static final int PANEL_SHADOW_COLOR = 0xFF8B8B8B;
    private static final int TEXT_COLOR = 0xFF404040;
    private static final int STATS_TOP_Y = 46;
    private static final int STATS_LINE_HEIGHT = 10;

    private static final String[] SLOT_LABELS = {"Head", "Chest", "Legs", "Feet", "Main", "Off"};

    private EditBox nameBox;

    public CompanionTokenScreen(CompanionTokenMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 200, CompanionTokenMenu.PLAYER_INV_Y + 76 + 6);
        this.titleLabelX = 10;
        this.titleLabelY = 8;
        this.inventoryLabelY = CompanionTokenMenu.PLAYER_INV_Y - 10;
    }

    @Override
    protected void init() {
        super.init();
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;

        nameBox = addRenderableWidget(new EditBox(font, x + 10, y + 22, 100, 16, Component.literal("Companion name")));
        nameBox.setMaxLength(32);
        nameBox.setValue(menu.currentName());

        addRenderableWidget(Button.builder(Component.literal("Rename"), b -> rename())
                .bounds(x + 130, y + 20, 60, 20).build());
    }

    private void rename() {
        String name = nameBox.getValue();
        if (name.isBlank()) {
            return;
        }
        var connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            connection.send(new ServerboundCustomPayloadPacket(new RenameCompanionTokenPayload(name)));
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);
        extractStats(graphics);
        for (int i = 0; i < CompanionTokenMenu.SLOT_ORDER.length; i++) {
            int x = CompanionTokenMenu.SLOT_X_START + i * CompanionTokenMenu.SLOT_SPACING;
            graphics.text(font, SLOT_LABELS[i], x - 2, CompanionTokenMenu.SLOT_Y - 10, TEXT_COLOR, false);
        }
        graphics.text(font, "Mount", CompanionTokenMenu.MOUNT_SLOT_X - 2, CompanionTokenMenu.MOUNT_SLOT_Y - 10, TEXT_COLOR, false);
    }

    private void extractStats(GuiGraphicsExtractor graphics) {
        LivingEntity companion = resolveLiveCompanion();
        if (companion == null) {
            graphics.text(font, "Not currently summoned", 10, STATS_TOP_Y, TEXT_COLOR, false);
            return;
        }
        graphics.text(font, String.format(Locale.ROOT, "Health: %.0f/%.0f", companion.getHealth(), companion.getMaxHealth()),
                10, STATS_TOP_Y, TEXT_COLOR, false);
        var attackAttr = companion.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        if (attackAttr != null) {
            graphics.text(font, String.format(Locale.ROOT, "Attack: %.1f", attackAttr.getValue()),
                    10, STATS_TOP_Y + STATS_LINE_HEIGHT, TEXT_COLOR, false);
        }
    }

    private LivingEntity resolveLiveCompanion() {
        UUID id = menu.summonedCompanionId().orElse(null);
        if (id == null || Minecraft.getInstance().level == null) {
            return null;
        }
        return Minecraft.getInstance().level.getEntity(id) instanceof LivingEntity living ? living : null;
    }

    private void drawSlotBackdrop(GuiGraphicsExtractor graphics, int xo, int yo, int localX, int localY) {
        int x = xo + localX;
        int y = yo + localY;
        graphics.fill(x - 1, y - 1, x + 17, y + 17, PANEL_OUTER_BORDER_COLOR);
        graphics.fill(x, y, x + 16, y + 16, PANEL_SHADOW_COLOR);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int xo = (this.width - this.imageWidth) / 2;
        int yo = (this.height - this.imageHeight) / 2;

        graphics.fill(xo, yo, xo + imageWidth, yo + imageHeight, PANEL_OUTER_BORDER_COLOR);
        graphics.fill(xo + 1, yo + 1, xo + imageWidth - 1, yo + imageHeight - 1, PANEL_HIGHLIGHT_COLOR);
        graphics.fill(xo + 2, yo + 2, xo + imageWidth - 1, yo + imageHeight - 1, PANEL_SHADOW_COLOR);
        graphics.fill(xo + 2, yo + 2, xo + imageWidth - 2, yo + imageHeight - 2, PANEL_BODY_COLOR);

        for (var slot : menu.slots) {
            drawSlotBackdrop(graphics, xo, yo, slot.x, slot.y);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
