package com.github.cerealklla.lyfe.knowledge;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;

/**
 * The Cartographyr sign/map authoring dialog (design doc Section 9.1) -- a toggle between free
 * text and picking one of the writer's own known {@code CONSTRUCTED} places, sent to the server
 * via {@link SubmitWritingPayload} on confirm. Deliberately a plain non-scrolling button list for
 * the known-places mode (v1 simplification, capped at a small count) rather than a full scrolling
 * widget -- see decisions.md.
 */
public final class WritingScreen extends Screen {

    private static final int MAX_LISTED_PLACES = 8;

    private final WritingTarget target;
    private final List<OpenWritingScreenPayload.KnownPlace> knownPlaces;
    private boolean freeTextMode = true;
    private EditBox textBox;
    private Long selectedPlaceId;
    private final List<Button> placeButtons = new ArrayList<>();

    public WritingScreen(WritingTarget target, List<OpenWritingScreenPayload.KnownPlace> knownPlaces) {
        super(Component.literal("Write a sign"));
        this.target = target;
        this.knownPlaces = knownPlaces;
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int top = height / 2 - 70;

        addRenderableWidget(Button.builder(Component.literal("Free Text"), b -> switchMode(true))
                .bounds(centerX - 100, top, 95, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Known Place"), b -> switchMode(false))
                .bounds(centerX + 5, top, 95, 20).build());

        textBox = new EditBox(font, centerX - 100, top + 30, 200, 20, Component.literal("Sign text"));
        textBox.setMaxLength(64);
        addRenderableWidget(textBox);

        int placeY = top + 30;
        int shown = 0;
        for (OpenWritingScreenPayload.KnownPlace place : knownPlaces) {
            if (shown >= MAX_LISTED_PLACES) {
                break;
            }
            Button placeButton = Button.builder(Component.literal(place.name() + " (" + place.embeddablePrecision() + ")"),
                            b -> selectPlace(place.entityId()))
                    .bounds(centerX - 100, placeY, 200, 20)
                    .build();
            addRenderableWidget(placeButton);
            placeButtons.add(placeButton);
            placeY += 22;
            shown++;
        }

        addRenderableWidget(Button.builder(Component.literal("Confirm"), b -> confirm())
                .bounds(centerX - 100, top + 200, 95, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
                .bounds(centerX + 5, top + 200, 95, 20).build());

        updateWidgetVisibility();
    }

    private void switchMode(boolean freeText) {
        this.freeTextMode = freeText;
        updateWidgetVisibility();
    }

    private void selectPlace(long entityId) {
        this.selectedPlaceId = entityId;
    }

    private void updateWidgetVisibility() {
        textBox.visible = freeTextMode;
        for (Button placeButton : placeButtons) {
            placeButton.visible = !freeTextMode;
        }
    }

    private void confirm() {
        Optional<String> freeText = freeTextMode && !textBox.getValue().isBlank()
                ? Optional.of(textBox.getValue())
                : Optional.empty();
        Optional<Long> chosenPlace = !freeTextMode && selectedPlaceId != null
                ? Optional.of(selectedPlaceId)
                : Optional.empty();

        if (freeText.isEmpty() && chosenPlace.isEmpty()) {
            return;
        }

        SubmitWritingPayload payload = new SubmitWritingPayload(target, freeText, chosenPlace);
        Minecraft.getInstance().getConnection().send(new ServerboundCustomPayloadPacket(payload));
        onClose();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int titleWidth = font.width(title);
        graphics.text(font, title, width / 2 - titleWidth / 2, height / 2 - 90, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
