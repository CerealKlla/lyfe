package com.github.cerealklla.lyfe.summoning.client;

import com.github.cerealklla.lyfe.summoning.ConfirmAllayBindPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;

/** "Give the Allay a name" dialog (design doc Section 27) -- direct mirror of {@code summoning.client.MountBindNameScreen}. */
public final class AllayBindNameScreen extends Screen {

    private final int allayEntityId;
    private EditBox nameBox;
    private Button bindButton;

    public AllayBindNameScreen(int allayEntityId) {
        super(Component.literal("Bind Allay"));
        this.allayEntityId = allayEntityId;
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int y = height / 2 - 10;

        nameBox = addRenderableWidget(new EditBox(font, centerX - 100, y, 200, 20, Component.literal("Allay name")));
        nameBox.setMaxLength(32);
        nameBox.setResponder(value -> bindButton.active = !value.isBlank());

        bindButton = addRenderableWidget(Button.builder(Component.literal("Bind"), b -> bind())
                .bounds(centerX - 100, y + 30, 95, 20).build());
        bindButton.active = false;
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
                .bounds(centerX + 5, y + 30, 95, 20).build());
    }

    private void bind() {
        String name = nameBox.getValue();
        if (name.isBlank()) {
            return;
        }
        var connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            connection.send(new ServerboundCustomPayloadPacket(new ConfirmAllayBindPayload(allayEntityId, name)));
        }
        onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
