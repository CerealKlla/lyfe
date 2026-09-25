package com.github.cerealklla.lyfe;

import java.lang.reflect.Field;

import com.github.cerealklla.lyfe.hunger.HungerOverlay;
import com.github.cerealklla.lyfe.knowledge.ClientWritingRequest;
import com.github.cerealklla.lyfe.knowledge.RequestWritingScreenPayload;
import com.github.cerealklla.lyfe.knowledge.WritingScreen;
import com.github.cerealklla.lyfe.knowledge.WritingTarget;
import com.github.cerealklla.lyfe.location.LocationOverlay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.gui.screens.inventory.SignEditScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = LyfeMod.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = LyfeMod.MODID, value = Dist.CLIENT)
public class LyfeModClient {

    // AbstractSignEditScreen#sign is protected, and this vanilla screen has no public accessor for
    // it -- a one-off reflective read (cached, looked up once at class-load) is simpler and lower-
    // risk than an access transformer for a single field this class only reads on GUI init, not a
    // hot path. Null if the lookup ever fails (a future MC version renaming the field), in which
    // case the button injection below just silently no-ops rather than crashing the client.
    private static final Field SIGN_FIELD;

    static {
        Field field;
        try {
            field = AbstractSignEditScreen.class.getDeclaredField("sign");
            field.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            LyfeMod.LOGGER.warn("Could not access AbstractSignEditScreen#sign -- the Cartographyr sign button will not appear", e);
            field = null;
        }
        SIGN_FIELD = field;
    }

    public LyfeModClient(ModContainer container) {
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        LyfeMod.LOGGER.info("Lyfe client setup");
    }

    // Registered unconditionally, same reasoning as LyfeMod#registerPayloads -- harmless if
    // Cartographyr isn't loaded (LocationOverlay just never has anything to show, since nothing
    // ever calls ClientLocationState.set(...) in that case).
    @SubscribeEvent
    static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(Identifier.fromNamespaceAndPath(LyfeMod.MODID, "location_overlay"), new LocationOverlay());
        // Design doc Section 10.0: replaces vanilla's hunger bar outright rather than supplementing
        // it, so there's one consistent bar showing the true (up to 30-icon) value, not two.
        event.replaceLayer(VanillaGuiLayers.FOOD_LEVEL, new HungerOverlay());
    }

    // Polls the zero-Cartographyr-refs bridge (see ClientWritingRequest's own class doc) for a
    // pending "open the writing dialog" request -- this is the one place allowed to touch
    // Minecraft/Screen for it, since this whole class is already client-dist-gated.
    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        ClientWritingRequest.takePending().ifPresent(request -> {
            if (Minecraft.getInstance().screen == null) {
                Minecraft.getInstance().setScreen(new WritingScreen(request.target(), request.knownPlaces()));
            }
        });
    }

    /**
     * Injects a "Cartographyr" button into vanilla's own {@code SignEditScreen} (2026-09-25, see
     * decisions.md) -- rides on vanilla's real sign-placement flow entirely instead of a special
     * trigger item. Only shown for a sign placed directly on a fence post ({@link BlockTags#FENCES}
     * below it) whose front text is still completely blank -- a practical proxy for "this is the
     * screen vanilla auto-opened right after initial placement," which is the only time this
     * screen naturally opens without the player deliberately re-editing. Clicking it closes the
     * vanilla screen (its own {@code removed()} submits whatever blank/partial text is currently
     * in the fields, same as any other vanilla sign edit) and asks the server for the known-places
     * picker via {@link RequestWritingScreenPayload}; declining (typing normal text and hitting
     * Done instead) leaves a completely ordinary vanilla sign.
     */
    @SubscribeEvent
    static void onScreenInit(ScreenEvent.Init.Post event) {
        if (SIGN_FIELD == null || !ModList.get().isLoaded("cartographyr")) {
            return;
        }
        if (!(event.getScreen() instanceof SignEditScreen screen)) {
            return;
        }
        SignBlockEntity sign;
        try {
            sign = (SignBlockEntity) SIGN_FIELD.get(screen);
        } catch (IllegalAccessException e) {
            return;
        }
        if (sign == null) {
            return;
        }
        var pos = sign.getBlockPos();
        var level = Minecraft.getInstance().level;
        if (level == null || !level.getBlockState(pos.below()).is(BlockTags.FENCES)) {
            return;
        }
        boolean blank = true;
        for (Component message : sign.getText(true).getMessages(false)) {
            if (!message.getString().isEmpty()) {
                blank = false;
                break;
            }
        }
        if (!blank) {
            return;
        }

        int centerX = screen.width / 2;
        int buttonY = screen.height / 4 + 144 - 24;
        event.addListener(Button.builder(Component.literal("Cartographyr"), b -> {
            Minecraft.getInstance().setScreen(null);
            var connection = Minecraft.getInstance().getConnection();
            if (connection != null) {
                connection.send(new ServerboundCustomPayloadPacket(new RequestWritingScreenPayload(WritingTarget.sign(pos))));
            }
        }).bounds(centerX - 100, buttonY, 200, 20).build());
    }
}
