package com.github.cerealklla.lyfe.recallcinite.client;

import com.github.cerealklla.lyfe.recallcinite.ConfirmRecallciniteBindPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;

/**
 * "Do you wish to bind to this location?" -- opened by a quick right-click tap on the Recallcinite
 * Totem (see {@code recallcinite.RecallciniteListener#openBindConfirmation}). Manual Confirm/Cancel
 * button layout mirrors {@code knowledge.WritingScreen}'s own pattern exactly, simplified further
 * since there's no list/selection here, just a yes/no.
 */
public final class RecallciniteBindConfirmScreen extends Screen {

    public RecallciniteBindConfirmScreen() {
        super(Component.literal("Bind Recallcinite Totem"));
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int buttonY = height / 2 + 10;

        addRenderableWidget(Button.builder(Component.literal("Yes"), b -> confirm())
                .bounds(centerX - 100, buttonY, 95, 20).build());
        addRenderableWidget(Button.builder(Component.literal("No"), b -> onClose())
                .bounds(centerX + 5, buttonY, 95, 20).build());
    }

    private void confirm() {
        var connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            connection.send(new ServerboundCustomPayloadPacket(new ConfirmRecallciniteBindPayload()));
        }
        onClose();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        // 0xFFFFFFFF, not 0xFFFFFF -- a bare 24-bit RGB value has alpha=0 (fully transparent), the
        // same real bug Blueprynts' construction screens hit earlier (see that mod's decisions.md).
        String title = "Bind Recallcinite Totem";
        int titleWidth = font.width(title);
        graphics.text(font, title, width / 2 - titleWidth / 2, height / 2 - 40, 0xFFFFFFFF);
        String message = "Do you wish to bind this totem to your current location?";
        int messageWidth = font.width(message);
        graphics.text(font, message, width / 2 - messageWidth / 2, height / 2 - 20, 0xFFFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
