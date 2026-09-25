package com.github.cerealklla.lyfe.knowledge;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;

/**
 * The Cartographyr sign/map authoring dialog (design doc Section 9.1) -- picks one of the
 * writer's own known {@code CONSTRUCTED} places, sent to the server via {@link
 * SubmitWritingPayload} on confirm. Free text was removed 2026-09-24 (see decisions.md) -- every
 * sign/map references a real place now.
 *
 * <p>Confirm/Cancel are anchored near the bottom of the screen (not computed relative to the
 * place list) so they're always on-screen regardless of window size or list length -- the
 * previous relative-offset layout was frequently off-screen, per playtest feedback. The list
 * itself is capped by whatever vertical space is actually available between the title and the
 * buttons, on top of {@link #MAX_LISTED_PLACES}, rather than a full scrolling widget -- a v1
 * simplification, see decisions.md.
 */
public final class WritingScreen extends Screen {

    private static final int MAX_LISTED_PLACES = 8;
    private static final int ROW_HEIGHT = 22;
    private static final int LIST_TOP = 40;
    private static final int BUTTON_BOTTOM_MARGIN = 30;

    private final WritingTarget target;
    private final List<OpenWritingScreenPayload.KnownPlace> knownPlaces;
    private Long selectedPlaceId;
    private Button confirmButton;

    public WritingScreen(WritingTarget target, List<OpenWritingScreenPayload.KnownPlace> knownPlaces) {
        super(Component.literal(target.kind() == WritingTarget.Kind.SIGN ? "Write a sign" : "Generate a map"));
        this.target = target;
        this.knownPlaces = knownPlaces;
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int buttonY = height - BUTTON_BOTTOM_MARGIN;

        int availableRows = Math.max(0, (buttonY - LIST_TOP) / ROW_HEIGHT);
        int shown = Math.min(MAX_LISTED_PLACES, Math.min(availableRows, knownPlaces.size()));

        int placeY = LIST_TOP;
        for (int i = 0; i < shown; i++) {
            OpenWritingScreenPayload.KnownPlace place = knownPlaces.get(i);
            addRenderableWidget(Button.builder(Component.literal(place.name() + " (" + place.embeddablePrecision() + ")"),
                            b -> selectPlace(place.entityId()))
                    .bounds(centerX - 100, placeY, 200, 20)
                    .build());
            placeY += ROW_HEIGHT;
        }

        confirmButton = addRenderableWidget(Button.builder(Component.literal("Confirm"), b -> confirm())
                .bounds(centerX - 100, buttonY, 95, 20).build());
        confirmButton.active = false;
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
                .bounds(centerX + 5, buttonY, 95, 20).build());
    }

    private void selectPlace(long entityId) {
        this.selectedPlaceId = entityId;
        confirmButton.active = true;
    }

    private void confirm() {
        if (selectedPlaceId == null) {
            return;
        }
        SubmitWritingPayload payload = new SubmitWritingPayload(target, selectedPlaceId);
        Minecraft.getInstance().getConnection().send(new ServerboundCustomPayloadPacket(payload));
        onClose();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int titleWidth = font.width(title);
        graphics.text(font, title, width / 2 - titleWidth / 2, 15, 0xFFFFFF);

        if (knownPlaces.isEmpty()) {
            String message = "You don't know any places to write about yet.";
            int messageWidth = font.width(message);
            graphics.text(font, message, width / 2 - messageWidth / 2, LIST_TOP, 0xFFAAAA);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
