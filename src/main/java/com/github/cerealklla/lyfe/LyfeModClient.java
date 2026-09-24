package com.github.cerealklla.lyfe;

import com.github.cerealklla.lyfe.hunger.HungerOverlay;
import com.github.cerealklla.lyfe.location.LocationOverlay;

import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = LyfeMod.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = LyfeMod.MODID, value = Dist.CLIENT)
public class LyfeModClient {
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
}
