package com.github.cerealklla.lyfe;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import com.github.cerealklla.lyfe.combat.CombatSkillListener;
import com.github.cerealklla.lyfe.cook.VanillaFoodRecipeStripper;
import com.github.cerealklla.lyfe.craft.ProficiencyDurabilityListener;
import com.github.cerealklla.lyfe.craft.VanillaRecipeStripper;
import com.github.cerealklla.lyfe.gathering.ExcavatorListener;
import com.github.cerealklla.lyfe.debug.DebugCommands;
import com.github.cerealklla.lyfe.durability.UnbreakableArmorListener;
import com.github.cerealklla.lyfe.durability.UnbreakableToolListener;
import com.github.cerealklla.lyfe.fishing.FishermanListener;
import com.github.cerealklla.lyfe.fishing.LockedChestUnlockListener;
import com.github.cerealklla.lyfe.fishing.LuckySpotPayload;
import com.github.cerealklla.lyfe.farming.FarmerListener;
import com.github.cerealklla.lyfe.gathering.GatheringListener;
import com.github.cerealklla.lyfe.gathering.LeafDecayAccelerator;
import com.github.cerealklla.lyfe.gathering.SaplingAutoReplant;
import com.github.cerealklla.lyfe.heartiness.HeartinessListener;
import com.github.cerealklla.lyfe.hunger.HungerListener;
import com.github.cerealklla.lyfe.location.ClientLocationState;
import com.github.cerealklla.lyfe.location.LocationPayload;
import com.github.cerealklla.lyfe.location.LocationTracker;
import com.github.cerealklla.lyfe.map.ClientMapState;
import com.github.cerealklla.lyfe.map.MapDataListener;
import com.github.cerealklla.lyfe.map.MapSettlementsPayload;
import com.github.cerealklla.lyfe.map.RequestMapDataPayload;
import com.github.cerealklla.lyfe.minimap.ClientMinimapState;
import com.github.cerealklla.lyfe.minimap.MinimapEntitiesPayload;
import com.github.cerealklla.lyfe.minimap.MinimapTracker;
import com.github.cerealklla.lyfe.repair.RepairInteractionListener;
import com.github.cerealklla.lyfe.knowledge.ClientWritingRequest;
import com.github.cerealklla.lyfe.knowledge.KnowledgeProximityTicker;
import com.github.cerealklla.lyfe.knowledge.OpenWritingScreenPayload;
import com.github.cerealklla.lyfe.knowledge.RequestWritingScreenPayload;
import com.github.cerealklla.lyfe.knowledge.SignListener;
import com.github.cerealklla.lyfe.knowledge.SubmitWritingPayload;
import com.github.cerealklla.lyfe.loot.RecipeNoteLootInjector;
import com.github.cerealklla.lyfe.loot.ResearchNoteLootInjector;
import com.github.cerealklla.lyfe.merchant.MerchantListener;
import com.github.cerealklla.lyfe.research.ResearchProgressPayload;
import com.github.cerealklla.lyfe.research.client.ClientResearchBarState;
import com.github.cerealklla.lyfe.reincarnation.ReincarnationListener;
import com.github.cerealklla.lyfe.registration.ModAttachments;
import com.github.cerealklla.lyfe.registration.ModBlockEntities;
import com.github.cerealklla.lyfe.registration.ModBlocks;
import com.github.cerealklla.lyfe.registration.ModEntities;
import com.github.cerealklla.lyfe.registration.ModFoodItems;
import com.github.cerealklla.lyfe.registration.ModItems;
import com.github.cerealklla.lyfe.registration.ModMenus;
import com.github.cerealklla.lyfe.registration.ModMobEffects;
import com.github.cerealklla.lyfe.rest.BedRestListener;
import com.github.cerealklla.lyfe.skill.Skills;
import com.github.cerealklla.lyfe.swim.SwimmerListener;
import com.github.cerealklla.lyfe.xpbar.XpGainPayload;
import com.github.cerealklla.lyfe.xpbar.client.ClientXpBarState;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.LootTableLoadEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// The value here must match the modId entry in META-INF/neoforge.mods.toml (sourced from mod_id in gradle.properties)
@Mod(LyfeMod.MODID)
public class LyfeMod {
    public static final String MODID = "lyfe";
    public static final Logger LOGGER = LogUtils.getLogger();

    public LyfeMod(IEventBus modEventBus, ModContainer modContainer) {
        ModAttachments.ATTACHMENT_TYPES.register(modEventBus);
        ModItems.DATA_COMPONENTS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModFoodItems.ITEMS.register(modEventBus);
        ModBlocks.BLOCKS.register(modEventBus);
        ModBlocks.ITEMS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModMenus.MENU_TYPES.register(modEventBus);
        ModMobEffects.MOB_EFFECTS.register(modEventBus);
        ModEntities.ENTITIES.register(modEventBus);
        Skills.bootstrap();

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerPayloads);

        NeoForge.EVENT_BUS.register(new GatheringListener());
        NeoForge.EVENT_BUS.register(new LeafDecayAccelerator());
        NeoForge.EVENT_BUS.register(new SaplingAutoReplant());
        NeoForge.EVENT_BUS.register(new FarmerListener());
        NeoForge.EVENT_BUS.register(new HungerListener());
        NeoForge.EVENT_BUS.register(new ReincarnationListener());
        NeoForge.EVENT_BUS.register(new SwimmerListener());
        NeoForge.EVENT_BUS.register(new HeartinessListener());
        NeoForge.EVENT_BUS.register(new BedRestListener());
        NeoForge.EVENT_BUS.register(new VanillaRecipeStripper());
        NeoForge.EVENT_BUS.register(new VanillaFoodRecipeStripper());
        NeoForge.EVENT_BUS.register(new FishermanListener());
        NeoForge.EVENT_BUS.register(new LockedChestUnlockListener());
        NeoForge.EVENT_BUS.register(new com.github.cerealklla.lyfe.cook.FoodStarterPackListener());
        NeoForge.EVENT_BUS.register(new CombatSkillListener());
        NeoForge.EVENT_BUS.register(new ExcavatorListener());
        NeoForge.EVENT_BUS.register(new ProficiencyDurabilityListener());
        NeoForge.EVENT_BUS.register(new UnbreakableToolListener());
        NeoForge.EVENT_BUS.register(new UnbreakableArmorListener());
        NeoForge.EVENT_BUS.register(new RepairInteractionListener());
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> DebugCommands.register(event.getDispatcher()));
        NeoForge.EVENT_BUS.addListener((LootTableLoadEvent event) -> ResearchNoteLootInjector.onLootTableLoad(event));
        NeoForge.EVENT_BUS.addListener((LootTableLoadEvent event) -> RecipeNoteLootInjector.onLootTableLoad(event));

        // Soft dependency (design doc Section 8): LocationTracker and SignListener both compile
        // against Cartographyr's real API (a compileOnly dependency, see build.gradle), but are
        // only ever constructed/registered -- and so only ever actually call that API -- when
        // Cartographyr is confirmed loaded at runtime. The sign/map items themselves (ModItems)
        // stay registered unconditionally, same as PlayerSkills et al -- only the interaction
        // logic that reads Cartographyr's data needs the gate.
        if (ModList.get().isLoaded("cartographyr")) {
            NeoForge.EVENT_BUS.register(new LocationTracker());
            NeoForge.EVENT_BUS.register(new SignListener());
            NeoForge.EVENT_BUS.register(new KnowledgeProximityTicker());
            // Minimap plot/settlement outlines (see MinimapTracker's own doc) -- the minimap's
            // terrain itself needs no Cartographyr data at all (pure client-side world sampling),
            // only this outline half does.
            NeoForge.EVENT_BUS.register(new MinimapTracker());
        }

        // Merchant skill (design doc / decisions.md, 2026-09-26): gated on Yconomics being loaded,
        // same soft-dependency shape as the Cartographyr-gated skills above -- the whole point of
        // this skill is Coin Purse tier integration, so there's no meaningful standalone mode.
        if (ModList.get().isLoaded("yconomics")) {
            NeoForge.EVENT_BUS.register(new MerchantListener());
        }

        // The old unconditional-on-login debug item grant (removed 2026-10-02, see decisions.md)
        // was replaced with a Dev Kyt contribution (/kyt getDev) the same day, but as of 2026-10-05
        // every item that contribution ever listed now has a real crafting recipe of its own (see
        // decisions.md's full remove-by-remove history) -- Research Bench and Fish Cleaning Station,
        // the last two, just gained theirs. With nothing left to contribute, this mod no longer
        // registers a Dev Kyt contribution at all.

        // Shop auto-seeding (design doc Section 19.9, 2026-10-05) -- registers this mod's own Zone
        // Types' Shop catalogs (Armorer/Blacksmith/Grocer/Restaurant) with Settlemynts' open
        // registry. See structure.ShopSeedCatalogs' own doc for why these four specifically live
        // here, not Blueprynts.
        if (ModList.get().isLoaded("settlemynts")) {
            com.github.cerealklla.lyfe.structure.ShopSeedCatalogs.registerAll();
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
                (payload, context) -> ClientLocationState.set(payload.line1(), payload.line2()));

        // Drives the transient XP bar HUD (replaces the old per-skill chat announcements,
        // 2026-10-02) -- sent centrally from api.Lyfe#addXp, so this handler covers every skill.
        event.registrar("1").playToClient(XpGainPayload.TYPE, XpGainPayload.STREAM_CODEC,
                (payload, context) -> ClientXpBarState.onXpGain(payload.skillId(), payload.oldXp(), payload.newXp()));

        // Fisherman's one-player-visible "lucky spot" bonus (design doc Section D) -- never
        // broadcast to anyone but the casting player, rendered client-side as a particle effect.
        event.registrar("1").playToClient(LuckySpotPayload.TYPE, LuckySpotPayload.STREAM_CODEC,
                (payload, context) -> com.github.cerealklla.lyfe.fishing.client.ClientLuckySpotState.set(payload.pos()));

        // Drives the transient research-progress HUD (2026-10-03, user request) -- sent from both
        // ResearchMenu (a real Research Bench attempt) and ResearchNoteItem (a note), so this one
        // handler covers both research paths.
        event.registrar("1").playToClient(ResearchProgressPayload.TYPE, ResearchProgressPayload.STREAM_CODEC,
                (payload, context) -> ClientResearchBarState.onProgress(
                        payload.resultId(), payload.oldPoints(), payload.newPoints(), payload.threshold()));

        // Registered unconditionally, same reasoning as LocationPayload above -- MinimapTracker is
        // only ever constructed (and so only ever actually sends this) when Cartographyr is loaded,
        // but the handler itself must exist regardless (one global network registry per modid).
        event.registrar("1").playToClient(MinimapEntitiesPayload.TYPE, MinimapEntitiesPayload.STREAM_CODEC,
                (payload, context) -> ClientMinimapState.setOutlines(payload.outlines()));

        // Client-side handler writes into the zero-Cartographyr-refs ClientWritingRequest bridge
        // (see its own class doc) rather than opening the Screen directly here -- this method must
        // stay harmless to class-load on a dedicated server, and Screen/Minecraft are client-only.
        event.registrar("1").playToClient(OpenWritingScreenPayload.TYPE, OpenWritingScreenPayload.STREAM_CODEC,
                (payload, context) -> ClientWritingRequest.request(
                        new ClientWritingRequest.Request(payload.target(), payload.knownPlaces())));

        // Guarded even though SignListener is only ever registered as a listener when Cartographyr
        // is loaded -- this handler could otherwise still be invoked by a stray/malicious packet on
        // a Cartographyr-less server, and this keeps that path an explicit no-op rather than relying
        // solely on the listener-registration gate.
        event.registrar("1").playToServer(SubmitWritingPayload.TYPE, SubmitWritingPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (ModList.get().isLoaded("cartographyr") && context.player() instanceof ServerPlayer serverPlayer) {
                        SignListener.handleSubmit(serverPlayer, payload);
                    }
                });

        // Client-initiated: sent when a player clicks the "Cartographyr" button injected into
        // vanilla's own SignEditScreen (see LyfeModClient) -- see SignListener#requestSignWritingScreen.
        event.registrar("1").playToServer(RequestWritingScreenPayload.TYPE, RequestWritingScreenPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (ModList.get().isLoaded("cartographyr") && context.player() instanceof ServerPlayer serverPlayer) {
                        SignListener.requestSignWritingScreen(serverPlayer, payload.target());
                    }
                });

        // map.client.MapScreen's settlement-dot data (2026-10-07) -- same Cartographyr-loaded guard
        // as every other handler here; without Cartographyr, the client simply never gets a reply
        // and the Map just shows no dots, same as "no settlements known yet."
        event.registrar("1").playToServer(RequestMapDataPayload.TYPE, RequestMapDataPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (ModList.get().isLoaded("cartographyr") && context.player() instanceof ServerPlayer serverPlayer) {
                        MapDataListener.handleRequest(serverPlayer);
                    }
                });
        event.registrar("1").playToClient(MapSettlementsPayload.TYPE, MapSettlementsPayload.STREAM_CODEC,
                (payload, context) -> ClientMapState.setSettlements(payload.settlements()));
    }

}
