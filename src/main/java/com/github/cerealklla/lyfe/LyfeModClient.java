package com.github.cerealklla.lyfe;

import java.lang.reflect.Field;

import com.github.cerealklla.lyfe.cook.client.CookingStructureScreen;
import com.github.cerealklla.lyfe.craft.client.CraftingStructureScreen;
import com.github.cerealklla.lyfe.fishing.CatchBagTooltip;
import com.github.cerealklla.lyfe.fishing.FixedLootTooltip;
import com.github.cerealklla.lyfe.fishing.client.ClientCatchBagTooltip;
import com.github.cerealklla.lyfe.fishing.client.ClientFixedLootTooltip;
import com.github.cerealklla.lyfe.fishing.client.ClientLuckySpotState;
import com.github.cerealklla.lyfe.hunger.HungerOverlay;
import com.github.cerealklla.lyfe.knowledge.ClientWritingRequest;
import com.github.cerealklla.lyfe.knowledge.RequestWritingScreenPayload;
import com.github.cerealklla.lyfe.fishing.client.FishCleaningScreen;
import com.github.cerealklla.lyfe.knowledge.WritingScreen;
import com.github.cerealklla.lyfe.knowledge.WritingTarget;
import com.github.cerealklla.lyfe.location.LocationOverlay;
import com.github.cerealklla.lyfe.minimap.ClientMinimapState;
import com.github.cerealklla.lyfe.minimap.MinimapOverlay;
import com.github.cerealklla.lyfe.client.FloatingIconRenderer;
import com.github.cerealklla.lyfe.cook.CookingStructureBlockEntity;
import com.github.cerealklla.lyfe.cook.FoodTierLadder;
import com.github.cerealklla.lyfe.craft.CraftingStructureBlockEntity;
import com.github.cerealklla.lyfe.craft.EquipmentTierLadder;
import com.github.cerealklla.lyfe.craft.client.EquipmentSkillWarningOverlay;
import com.github.cerealklla.lyfe.durability.client.EquipmentStatusOverlay;
import com.github.cerealklla.lyfe.registration.ModBlockEntities;
import com.github.cerealklla.lyfe.registration.ModEntities;
import com.github.cerealklla.lyfe.registration.ModMenus;
import com.github.cerealklla.lyfe.repair.client.RepairStructureScreen;
import com.github.cerealklla.lyfe.research.client.ResearchScreen;
import com.github.cerealklla.lyfe.rest.client.SeatEntityRenderer;
import com.github.cerealklla.lyfe.skill.client.SkillsScreen;
import com.github.cerealklla.lyfe.swim.SwimmerOverlay;
import com.github.cerealklla.lyfe.research.client.ClientResearchBarState;
import com.github.cerealklla.lyfe.research.client.ResearchBarOverlay;
import com.github.cerealklla.lyfe.xpbar.client.ClientXpBarState;
import com.github.cerealklla.lyfe.xpbar.client.XpBarOverlay;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.gui.screens.inventory.SignEditScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import net.minecraft.client.renderer.RenderPipelines;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = LyfeMod.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = LyfeMod.MODID, value = Dist.CLIENT)
public class LyfeModClient {

    // The Equipment Status overlay's solid-color item-silhouette draw (durability.client.
    // EquipmentStatusOverlay) reuses vanilla's own rendertype_outline shader/vertex-format (same one
    // behind the Glowing-effect entity outline, via RenderPipelines.OUTLINE_SNIPPET) but needs its
    // own pipeline rather than OUTLINE_CULL/OUTLINE_NO_CULL directly: those inherit whatever cull/
    // depth-test state is active for the (unrelated) separate entity-outline target they're normally
    // used against, which isn't safe to assume when drawing directly into the main framebuffer from
    // a GuiLayer. Explicit here instead: no culling (a flipped-Y icon transform would otherwise have
    // its winding order reversed and get silently backface-culled) and an always-pass, no-write depth
    // test (so it draws regardless of whatever's left in the depth buffer from 3D world rendering).
    public static final RenderPipeline ITEM_SILHOUETTE_PIPELINE = RenderPipeline.builder(RenderPipelines.OUTLINE_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(LyfeMod.MODID, "pipeline/item_silhouette"))
            .withCull(false)
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .build();

    @SubscribeEvent
    static void onRegisterRenderPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(ITEM_SILHOUETTE_PIPELINE);
    }

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

    // Press-to-step zoom, not continuous scroll -- simpler input handling, and default-bound to
    // "]"/"[" (unbound would be equally valid; these are just accessible non-conflicting defaults).
    // Minimap zoom is always available (no Cartographyr gate on the keys themselves -- the *range*
    // they can reach is what's level-gated, see MinimapZoom).
    private static final KeyMapping MINIMAP_ZOOM_IN = new KeyMapping(
            "key.lyfe.minimap_zoom_in", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_BRACKET, KeyMapping.Category.MISC);
    private static final KeyMapping MINIMAP_ZOOM_OUT = new KeyMapping(
            "key.lyfe.minimap_zoom_out", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_BRACKET, KeyMapping.Category.MISC);

    // Auto-Rotate toggle, wired up 2026-09-29 for a live test -- see ClientMinimapState's own doc.
    private static final KeyMapping MINIMAP_TOGGLE_ROTATION = new KeyMapping(
            "key.lyfe.minimap_toggle_rotation", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_O, KeyMapping.Category.MISC);

    // Opens the Skills screen (design doc Section 13) -- "K" is free (zoom uses "["/"]", minimap
    // rotation uses "O"), flagged as an easy-to-rebind placeholder like every other keybind here.
    private static final KeyMapping OPEN_SKILLS = new KeyMapping(
            "key.lyfe.open_skills", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, KeyMapping.Category.MISC);

    public LyfeModClient(ModContainer container) {
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        LyfeMod.LOGGER.info("Lyfe client setup");
    }

    @SubscribeEvent
    static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(MINIMAP_ZOOM_IN);
        event.register(MINIMAP_ZOOM_OUT);
        event.register(MINIMAP_TOGGLE_ROTATION);
        event.register(OPEN_SKILLS);
    }

    // Registered unconditionally, same reasoning as LyfeMod#registerPayloads -- harmless if
    // Cartographyr isn't loaded (LocationOverlay just never has anything to show, since nothing
    // ever calls ClientLocationState.set(...) in that case; MinimapOverlay still renders real
    // terrain regardless -- that part needs no Cartographyr data at all, only the nearby-plot/
    // settlement outlines do, and those are simply an empty list without it).
    @SubscribeEvent
    static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(Identifier.fromNamespaceAndPath(LyfeMod.MODID, "minimap_overlay"), new MinimapOverlay());
        event.registerAboveAll(Identifier.fromNamespaceAndPath(LyfeMod.MODID, "location_overlay"), new LocationOverlay());
        event.registerAboveAll(Identifier.fromNamespaceAndPath(LyfeMod.MODID, "xp_bar_overlay"), new XpBarOverlay());
        event.registerAboveAll(Identifier.fromNamespaceAndPath(LyfeMod.MODID, "research_bar_overlay"), new ResearchBarOverlay());
        // Red-caution "not enough skill level for this tool/weapon" warning, 2026-10-06 user
        // request -- same above-hotbar placement convention as Settlemynts' PlotValidityOverlay.
        event.registerAboveAll(Identifier.fromNamespaceAndPath(LyfeMod.MODID, "equipment_skill_warning_overlay"), new EquipmentSkillWarningOverlay());
        // Left-edge equipment status panel, 2026-10-07 user request -- the only visual signal that
        // something has gone critically low now that Unbreakable Equipment stops items from
        // actually breaking outright.
        event.registerAboveAll(Identifier.fromNamespaceAndPath(LyfeMod.MODID, "equipment_status_overlay"), new EquipmentStatusOverlay());
        // Design doc Section 10.0: replaces vanilla's hunger bar outright rather than supplementing
        // it, so there's one consistent bar showing the true (up to 30-icon) value, not two.
        event.replaceLayer(VanillaGuiLayers.FOOD_LEVEL, new HungerOverlay());
        // Design doc Section 5: same reasoning, replaces vanilla's hardcoded 10-bubble air bar with
        // one that can show up to 30 as Swimmer levels.
        event.replaceLayer(VanillaGuiLayers.AIR_LEVEL, new SwimmerOverlay());
        // Disables vanilla's sleep fade-to-black entirely (2026-10-03 user request) -- there's no
        // removeLayer, so a true no-op replacement is the way to disable a vanilla layer outright,
        // same mechanism as the two replacements above. Requested alongside rest.BedRestListener's
        // own fix for the forced-wake-every-5-seconds bug, whose workaround re-triggers this fade on
        // every resume -- see that class's doc for the full story.
        event.replaceLayer(VanillaGuiLayers.SLEEP_OVERLAY, (graphics, deltaTracker) -> {
        });
    }

    // Catch Bag needs its own pounds-based progress bar, not vanilla's item-count-based one -- see
    // fishing.CatchBagTooltip's own doc for the live bug this replaced.
    @SubscribeEvent
    static void onRegisterTooltipComponents(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(CatchBagTooltip.class, ClientCatchBagTooltip::new);
        event.register(FixedLootTooltip.class, ClientFixedLootTooltip::new);
    }

    @SubscribeEvent
    static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.SEAT.get(), SeatEntityRenderer::new);

        // Floating structure icons (2026-10-05 user request, extended same day to every custom
        // crafting/cooking/research/fish-cleaning structure, not just the crafting ladder) -- one
        // generic FloatingIconRenderer per block type, parameterized by how that type's icon is
        // resolved. Crafting structures: that tier's own Sword (tier IS the highest equipment tier
        // craftable there). Cooking structures: that tier's own unique signature dish (Track B).
        // Research Bench/Fish Cleaning Station: untiered, one fixed icon each.
        event.registerBlockEntityRenderer(ModBlockEntities.CRAFTING_STATION.get(), context -> new FloatingIconRenderer<>(LyfeModClient::craftingStructureIcon));
        event.registerBlockEntityRenderer(ModBlockEntities.CRAFTING_MAT.get(), context -> new FloatingIconRenderer<>(LyfeModClient::craftingStructureIcon));
        event.registerBlockEntityRenderer(ModBlockEntities.TINKER_BENCH.get(), context -> new FloatingIconRenderer<>(LyfeModClient::craftingStructureIcon));
        event.registerBlockEntityRenderer(ModBlockEntities.CRAFTING_TABLE_LYFE.get(), context -> new FloatingIconRenderer<>(LyfeModClient::craftingStructureIcon));
        event.registerBlockEntityRenderer(ModBlockEntities.ENGINEERS_BENCH.get(), context -> new FloatingIconRenderer<>(LyfeModClient::craftingStructureIcon));

        event.registerBlockEntityRenderer(ModBlockEntities.COOKING_STATION.get(), context -> new FloatingIconRenderer<>(LyfeModClient::cookingStructureIcon));
        event.registerBlockEntityRenderer(ModBlockEntities.GRILL.get(), context -> new FloatingIconRenderer<>(LyfeModClient::cookingStructureIcon));
        event.registerBlockEntityRenderer(ModBlockEntities.STOVE.get(), context -> new FloatingIconRenderer<>(LyfeModClient::cookingStructureIcon));
        event.registerBlockEntityRenderer(ModBlockEntities.OVEN.get(), context -> new FloatingIconRenderer<>(LyfeModClient::cookingStructureIcon));
        event.registerBlockEntityRenderer(ModBlockEntities.CHEF_SET.get(), context -> new FloatingIconRenderer<>(LyfeModClient::cookingStructureIcon));

        event.registerBlockEntityRenderer(ModBlockEntities.RESEARCH_BENCH.get(),
                context -> new FloatingIconRenderer<>(be -> new ItemStack(Items.BOOK)));
        event.registerBlockEntityRenderer(ModBlockEntities.FISH_CLEANING_STATION.get(),
                context -> new FloatingIconRenderer<>(be -> new ItemStack(Items.COD)));
    }

    private static ItemStack craftingStructureIcon(CraftingStructureBlockEntity structure) {
        Identifier itemId = EquipmentTierLadder.structureIconItemId(structure.tier());
        return new ItemStack(BuiltInRegistries.ITEM.getValue(itemId));
    }

    private static ItemStack cookingStructureIcon(CookingStructureBlockEntity structure) {
        Identifier itemId = FoodTierLadder.trackB(structure.tier());
        return new ItemStack(BuiltInRegistries.ITEM.getValue(itemId));
    }

    // Polls the zero-Cartographyr-refs bridge (see ClientWritingRequest's own class doc) for a
    // pending "open the writing dialog" request -- this is the one place allowed to touch
    // Minecraft/Screen for it, since this whole class is already client-dist-gated.
    @SubscribeEvent
    static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.RESEARCH_BENCH.get(), ResearchScreen::new);
        event.register(ModMenus.FISH_CLEANING_STATION.get(), FishCleaningScreen::new);
        event.register(ModMenus.CRAFTING_STRUCTURE.get(), CraftingStructureScreen::new);
        event.register(ModMenus.COOKING_STRUCTURE.get(), CookingStructureScreen::new);
        event.register(ModMenus.REPAIR_STRUCTURE.get(), RepairStructureScreen::new);
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        ClientXpBarState.tick();
        ClientResearchBarState.tick();
        ClientLuckySpotState.tick();

        ClientWritingRequest.takePending().ifPresent(request -> {
            if (Minecraft.getInstance().screen == null) {
                Minecraft.getInstance().setScreen(new WritingScreen(request.target(), request.knownPlaces()));
            }
        });

        // consumeClick() is edge-triggered (fires once per press, not held-repeat) -- exactly the
        // "step the zoom level" behavior wanted here, same idiom vanilla itself uses for one-shot
        // keybind actions.
        while (MINIMAP_ZOOM_IN.consumeClick()) {
            ClientMinimapState.setZoomStep(ClientMinimapState.zoomStep() - 1);
        }
        while (MINIMAP_ZOOM_OUT.consumeClick()) {
            ClientMinimapState.setZoomStep(ClientMinimapState.zoomStep() + 1);
        }
        while (MINIMAP_TOGGLE_ROTATION.consumeClick()) {
            ClientMinimapState.setAutoRotate(!ClientMinimapState.autoRotate());
        }
        while (OPEN_SKILLS.consumeClick()) {
            if (Minecraft.getInstance().screen == null) {
                Minecraft.getInstance().setScreen(new SkillsScreen());
            }
        }
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
