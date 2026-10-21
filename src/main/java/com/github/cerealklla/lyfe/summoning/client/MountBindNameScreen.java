package com.github.cerealklla.lyfe.summoning.client;

import com.github.cerealklla.lyfe.summoning.ConfirmMountBindPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;

/**
 * "Give the mount a name" dialog (design doc Section 26) -- opened after the server validates a
 * bind request, mirrors {@code knowledge.WritingScreen}'s own payload-driven shape but with a real
 * text field ({@link EditBox}), same construction/read-back pattern as
 * {@code settlemynts.founding.client.FoundingScreen}'s own name box.
 */
public final class MountBindNameScreen extends Screen {

    private final int mountEntityId;
    private EditBox nameBox;
    private Button bindButton;

    public MountBindNameScreen(int mountEntityId) {
        super(Component.literal("Bind to Summoning Stone"));
        this.mountEntityId = mountEntityId;
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int y = height / 2 - 10;

        nameBox = addRenderableWidget(new EditBox(font, centerX - 100, y, 200, 20, Component.literal("Mount name")));
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
            connection.send(new ServerboundCustomPayloadPacket(new ConfirmMountBindPayload(mountEntityId, name)));
        }
        onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
