package com.github.cerealklla.lyfe;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

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
}
