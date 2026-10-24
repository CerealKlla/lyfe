package com.github.cerealklla.lyfe.summoning.client;

import java.util.Locale;
import java.util.UUID;

import com.github.cerealklla.lyfe.registration.ModAttachments;
import com.github.cerealklla.lyfe.registration.ModAttributes;
import com.github.cerealklla.lyfe.summoning.AllayBottleMenu;
import com.github.cerealklla.lyfe.summoning.RenameAllayBottlePayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;

/**
 * The Equip/Gather GUI's real screen (design doc Section 27) -- visual style directly copied from
 * {@code socket.client.SocketingScreen} (beveled flat panel, no texture file), plus a rename
 * {@link EditBox}/button baked directly into the screen itself (not a menu slot) -- "The Allay GUI
 * will allow you to rename," same EditBox-plus-button-in-a-plain-screen idiom used everywhere else
 * in this suite for a one-off text field.
 *
 * <p><b>Overlap fix + live stats, 2026-10-24</b> (explicit user request: "can you fix the overlaps
 * and also show the Allay's stats in here") -- the Equip/Gather labels used to sit squarely inside
 * the Rename button's own bounds (both {@link AllayBottleMenu}'s old {@code EQUIP_SLOT_Y}=46 and the
 * button's y=20-40 range overlapped); the Equip/Gather row moved down (see that class's own doc) to
 * make room for both a clean gap and this new stats block. Stats are resolved fresh every frame
 * straight off the client's own level via {@link AllayBottleMenu#summonedAllayId()} (itself read
 * from the held bottle's already-synced item component, same zero-new-networking shape {@code
 * currentName} already uses) -- same "never cache, always re-derive" discipline every other live-
 * attribute reader in this suite follows, since Health/Mana/Mana Regen can all change at any moment.
 */
public final class AllayBottleScreen extends AbstractContainerScreen<AllayBottleMenu> {

    private static final int PANEL_BODY_COLOR = 0xFFC6C6C6;
    private static final int PANEL_OUTER_BORDER_COLOR = 0xFF373737;
    private static final int PANEL_HIGHLIGHT_COLOR = 0xFFFFFFFF;
    private static final int PANEL_SHADOW_COLOR = 0xFF8B8B8B;
    private static final int TEXT_COLOR = 0xFF404040;
    private static final int STATS_TOP_Y = 46;
    private static final int STATS_LINE_HEIGHT = 10;

    private EditBox nameBox;

    public AllayBottleScreen(AllayBottleMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, AllayBottleMenu.PLAYER_INV_Y + 76 + 6);
        this.titleLabelX = 10;
        this.titleLabelY = 8;
        this.inventoryLabelY = AllayBottleMenu.PLAYER_INV_Y - 10;
    }

    @Override
    protected void init() {
        super.init();
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;

        nameBox = addRenderableWidget(new EditBox(font, x + 10, y + 22, 100, 16, Component.literal("Allay name")));
        nameBox.setMaxLength(32);
        nameBox.setValue(menu.currentName());

        addRenderableWidget(Button.builder(Component.literal("Rename"), b -> rename())
                .bounds(x + 116, y + 20, 50, 20).build());
    }

    private void rename() {
        String name = nameBox.getValue();
        if (name.isBlank()) {
            return;
        }
        var connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            connection.send(new ServerboundCustomPayloadPacket(new RenameAllayBottlePayload(name)));
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);
        extractStats(graphics);
        graphics.text(font, "Equip", AllayBottleMenu.EQUIP_SLOT_X - 2, AllayBottleMenu.EQUIP_SLOT_Y - 10, 0xFF404040, false);
        graphics.text(font, "Gather", AllayBottleMenu.GATHER_SLOT_X - 2, AllayBottleMenu.GATHER_SLOT_Y - 10, 0xFF404040, false);
    }

    private void extractStats(GuiGraphicsExtractor graphics) {
        LivingEntity allay = resolveLiveAllay();
        if (allay == null) {
            graphics.text(font, "Not currently summoned", 10, STATS_TOP_Y, TEXT_COLOR, false);
            return;
        }
        graphics.text(font, String.format(Locale.ROOT, "Health: %.0f/%.0f", allay.getHealth(), allay.getMaxHealth()),
                10, STATS_TOP_Y, TEXT_COLOR, false);
        double mana = allay.getData(ModAttachments.ENTITY_MANA).getCurrentMana();
        double maxMana = allay.getAttributeValue(ModAttributes.MAX_MANA);
        graphics.text(font, String.format(Locale.ROOT, "Mana: %.0f/%.0f", mana, maxMana),
                10, STATS_TOP_Y + STATS_LINE_HEIGHT, TEXT_COLOR, false);
        double manaRegen = allay.getAttributeValue(ModAttributes.MANA_REGEN);
        graphics.text(font, String.format(Locale.ROOT, "Mana Regen: +%.1f/s", manaRegen),
                10, STATS_TOP_Y + STATS_LINE_HEIGHT * 2, TEXT_COLOR, false);
    }

    /** {@code null} if no Allay is currently summoned, or it's no longer loaded (gone out of render distance, despawned, etc.). */
    private LivingEntity resolveLiveAllay() {
        UUID id = menu.summonedAllayId().orElse(null);
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
