package com.github.cerealklla.lyfe;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import com.github.cerealklla.lyfe.debug.DebugCommands;
import com.github.cerealklla.lyfe.gathering.GatheringListener;
import com.github.cerealklla.lyfe.hunger.HungerListener;
import com.github.cerealklla.lyfe.location.ClientLocationState;
import com.github.cerealklla.lyfe.location.LocationPayload;
import com.github.cerealklla.lyfe.location.LocationTracker;
import com.github.cerealklla.lyfe.registration.ModAttachments;
import com.github.cerealklla.lyfe.skill.Skills;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// The value here must match the modId entry in META-INF/neoforge.mods.toml (sourced from mod_id in gradle.properties)
@Mod(LyfeMod.MODID)
public class LyfeMod {
    public static final String MODID = "lyfe";
    public static final Logger LOGGER = LogUtils.getLogger();

    public LyfeMod(IEventBus modEventBus, ModContainer modContainer) {
        ModAttachments.ATTACHMENT_TYPES.register(modEventBus);
        Skills.bootstrap();

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerPayloads);

        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(new GatheringListener());
        NeoForge.EVENT_BUS.register(new HungerListener());
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> DebugCommands.register(event.getDispatcher()));

        // Soft dependency (design doc Section 8): LocationTracker's class compiles against
        // Cartographyr's real API (a compileOnly dependency, see build.gradle), but is only ever
        // constructed/registered -- and so only ever actually calls that API -- when Cartographyr
        // is confirmed loaded at runtime.
        if (ModList.get().isLoaded("cartographyr")) {
            NeoForge.EVENT_BUS.register(new LocationTracker());
        }
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Lyfe common setup");
    }

    // Registered unconditionally (not gated on Cartographyr being loaded): harmless to register
    // the schema even if it's never used, and it must happen from a single call site regardless --
    // RegisterPayloadHandlersEvent shares one global network registry per modid, so a second
    // registration call anywhere else throws "already registered" (a mistake already made and
    // fixed once in Cartographyr's own version of this feature; see that mod's decisions.md).
    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(LocationPayload.TYPE, LocationPayload.STREAM_CODEC,
                (payload, context) -> ClientLocationState.set(payload.name()));
    }

    /**
     * DEBUG ONLY — grants raw materials for testing the sign/map mechanic (design doc Section 9.1)
     * before that mechanic exists: oak signs, oak fences (to place as mounting posts), and blank
     * maps. Deliberately naive: fires on every login, not just a brand-new character, since
     * re-supplying test items each session is a minor inconvenience at worst and precisely
     * detecting "first-ever spawn" adds complexity not worth it for throwaway debug tooling. Must
     * be removed or gated behind a real debug flag before any actual release.
     */
    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) {
            return;
        }
        player.addItem(new ItemStack(Items.OAK_SIGN, 16));
        player.addItem(new ItemStack(Items.OAK_FENCE, 16));
        player.addItem(new ItemStack(Items.MAP, 8));
        LOGGER.info("Granted debug sign/fence/map testing items to {}", player.getName().getString());
    }
}
