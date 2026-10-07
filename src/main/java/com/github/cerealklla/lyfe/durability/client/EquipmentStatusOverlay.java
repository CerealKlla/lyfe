package com.github.cerealklla.lyfe.durability.client;

import java.util.ArrayList;
import java.util.List;

import com.github.cerealklla.lyfe.LyfeModClient;
import com.github.cerealklla.lyfe.craft.EquipmentTierLadder;
import com.github.cerealklla.lyfe.durability.DurabilityStatus;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.ArmorType;
import net.neoforged.neoforge.client.gui.GuiLayer;

/**
 * Floating left-edge "equipment status" panel (2026-10-07 user request, follow-up to Unbreakable
 * Equipment): since items no longer actually break, this is the player's only at-a-glance signal
 * that something has gone critically low. Shows a solid-color, alpha-masked silhouette of each of
 * the 6 equipment slots (helmet/chestplate/leggings/boots/mainhand/offhand) -- the item's own exact
 * icon shape, recolored, no background square -- tinted by {@link DurabilityStatus#tierFor}, only
 * once at least one of them is at or below the grey threshold. Layout confirmed against the user's
 * own mockup: a 4-row left column (armor, in {@link EquipmentTierLadder#ARMOR_TYPES} order) and a
 * 2-row right column (mainhand/offhand) vertically centered against the armor column's middle two
 * rows.
 *
 * <p>No networking of any kind -- the client's own {@code LocalPlayer} already has full,
 * damage-accurate {@code ItemStack} data for its own equipped slots via vanilla's ordinary
 * equipment sync, same as {@code craft.client.EquipmentSkillWarningOverlay} already assumes for
 * these same 6 slots.
 *
 * <p><b>Rendering technique</b> (first of its kind in this project -- see decisions.md for the full
 * investigation trail, including two dead ends live-tested and ruled out): a first attempt drew the
 * plain item icon plus a translucent {@code fill()} square over it, which the user correctly
 * rejected as "a background square." A second attempt built the item's real {@code BakedQuad}s
 * (via a plain {@code ItemStackRenderState} fed through its own public {@code submit(PoseStack,
 * SubmitNodeCollector, int, int, int)} entry point with vanilla's own {@code SubmitNodeStorage},
 * which applies each layer's correct GUI-display transform/rotation/scale for free -- the same
 * mechanism vanilla's own {@code GuiItemAtlas#drawToSlot} uses to bake item icons, whose exact base
 * pose transform is copied below) and drew them with a forced solid vertex color through a custom
 * pipeline reusing vanilla's {@code core/rendertype_outline} shader (the same one behind the
 * Glowing-effect entity outline) -- correct in principle, but issued as a raw, ad-hoc
 * {@code MultiBufferSource.immediate(...).endBatch()} call mid-{@code GuiLayer#render}, which
 * turned out not to actually land in the presented frame at all (confirmed live: even a trivial
 * solid-color sanity-check quad through a 100% vanilla pipeline never appeared, while debug logging
 * showed correct data -- real quads, right tiers -- being submitted every frame; the bug was never
 * in the item/tint logic, only in how the draw was being issued). The real fix: submit through
 * {@link GuiGraphicsExtractor#submitGuiElementRenderState}, a NeoForge-added extension point that
 * feeds a custom {@link GuiElementRenderState} into the exact same deferred queue {@code fill()}/
 * {@code item()} already use -- guaranteeing the right projection, target, and timing for free,
 * rather than trying to replicate any of that by hand.
 */
public final class EquipmentStatusOverlay implements GuiLayer {

    private static final int ICON_SIZE = 16;
    private static final int ROW_SPACING = 18;
    private static final int MARGIN_LEFT = 8;
    private static final int COLUMN_SPACING = 20;
    private static final int ARMOR_ROW_COUNT = 4;

    private record Entry(ItemStack stack, int column, int row) {
    }

    private record ItemSilhouetteRenderState(
            List<BakedQuad> quads, PoseStack.Pose pose, int tintArgb, ScreenRectangle bounds, ScreenRectangle scissor
    ) implements GuiElementRenderState {

        @Override
        public void buildVertices(VertexConsumer vertexConsumer) {
            QuadInstance instance = new QuadInstance();
            instance.setColor(tintArgb);
            for (BakedQuad quad : quads) {
                vertexConsumer.putBakedQuad(pose, quad, instance);
            }
        }

        @Override
        public RenderPipeline pipeline() {
            return LyfeModClient.ITEM_SILHOUETTE_PIPELINE;
        }

        @Override
        public TextureSetup textureSetup() {
            AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_ITEMS);
            return TextureSetup.singleTexture(texture.getTextureView(), texture.getSampler());
        }

        @Override
        public ScreenRectangle scissorArea() {
            return scissor;
        }
    }

    @Override
    public void render(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        List<Entry> entries = new ArrayList<>();
        List<ArmorType> armorTypes = EquipmentTierLadder.ARMOR_TYPES;
        for (int row = 0; row < armorTypes.size(); row++) {
            ItemStack piece = player.getItemBySlot(armorTypes.get(row).getSlot());
            if (!piece.isEmpty() && piece.isDamageableItem()) {
                entries.add(new Entry(piece, 0, row));
            }
        }
        addIfDamageable(entries, player.getMainHandItem(), 1, 1);
        addIfDamageable(entries, player.getOffhandItem(), 1, 2);

        if (entries.isEmpty()) {
            return;
        }
        double lowestPercent = Double.MAX_VALUE;
        for (Entry entry : entries) {
            double percent = DurabilityStatus.percentRemaining(entry.stack().getDamageValue(), entry.stack().getMaxDamage());
            lowestPercent = Math.min(lowestPercent, percent);
        }
        if (lowestPercent > 25.0) {
            return;
        }

        ScreenRectangle scissor = guiGraphics.peekScissorStack();
        int topY = guiGraphics.guiHeight() / 2 - (ARMOR_ROW_COUNT * ROW_SPACING) / 2;
        for (Entry entry : entries) {
            int x = MARGIN_LEFT + entry.column() * COLUMN_SPACING;
            int y = topY + entry.row() * ROW_SPACING;
            DurabilityStatus.Tier tier = DurabilityStatus.tierFor(entry.stack().getDamageValue(), entry.stack().getMaxDamage());
            submitSilhouette(guiGraphics, player, entry.stack(), x, y, tier.tintColor, scissor);
        }
    }

    private static void submitSilhouette(GuiGraphicsExtractor guiGraphics, LocalPlayer player, ItemStack stack, int x, int y, int tintArgb, ScreenRectangle scissor) {
        ItemStackRenderState renderState = new ItemStackRenderState();
        Minecraft.getInstance().getItemModelResolver()
                .updateForTopItem(renderState, stack, ItemDisplayContext.GUI, player.level(), player, 0);

        SubmitNodeStorage storage = new SubmitNodeStorage();
        PoseStack poseStack = new PoseStack();
        poseStack.pushPose();
        // Exact base transform vanilla's own GuiItemAtlas#drawToSlot uses to bake a 16x16 item icon
        // into a slot at (x, y) -- centers on the slot, Y negated (model Y-up vs screen Y-down).
        poseStack.translate(x + 8.0F, y + 8.0F, 0.0F);
        poseStack.scale(ICON_SIZE, -ICON_SIZE, ICON_SIZE);
        renderState.submit(poseStack, storage, 15728880, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();

        ScreenRectangle bounds = new ScreenRectangle(x, y, ICON_SIZE, ICON_SIZE);
        for (SubmitNodeStorage.ItemSubmit itemSubmit : storage.order(0).getItemSubmits()) {
            guiGraphics.submitGuiElementRenderState(
                    new ItemSilhouetteRenderState(itemSubmit.quads(), itemSubmit.pose(), tintArgb, bounds, scissor));
        }
    }

    private static void addIfDamageable(List<Entry> entries, ItemStack stack, int column, int row) {
        if (!stack.isEmpty() && stack.isDamageableItem()) {
            entries.add(new Entry(stack, column, row));
        }
    }
}
