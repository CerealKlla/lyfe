package com.github.cerealklla.lyfe.client;

import java.util.function.Function;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Floats a purely-visual, non-interactive item above any of this mod's custom structure blocks
 * (design doc, 2026-10-05 user request: "it's hard to tell these apart from the vanilla structures
 * which is why I want these icons added" -- extended the same day from crafting structures only to
 * every custom crafting/cooking/research/fish-cleaning structure in the mod). One generic, reusable
 * renderer parameterized by an {@code iconResolver} function instead of one subclass per structure
 * type -- the same "one class, many block types" shape {@code CraftingStructureMenu} already uses
 * for its 5 tiers.
 */
public class FloatingIconRenderer<T extends BlockEntity> implements BlockEntityRenderer<T, FloatingIconRenderState> {

    private static final double FLOAT_HEIGHT = 1.3;
    private static final float SCALE = 0.5F;
    private static final float DEGREES_PER_TICK = 1.0F;

    // Same sentinel LevelRenderer.getLightCoords/BlockEntityRenderState.extractBase themselves use
    // for "no level"/emissive rendering -- both channels maxed. A real live bug, 2026-10-05: using
    // the structure's own real ambient light (state.lightCoords from extractBase) rendered the icon
    // solid black outdoors in daylight despite the surrounding scene being clearly well lit; the
    // real cause wasn't pinned down with confidence after checking it against a real working vanilla
    // precedent (BrushableBlockRenderer uses the identical lightCoords/outlineColor shape). Forcing
    // full bright sidesteps whatever that was AND better serves the feature's actual purpose anyway
    // -- an identification marker that could darken to unreadable in shade/at night would defeat the
    // point ("hard to tell these apart from the vanilla structures").
    private static final int FULL_BRIGHT = 15728880;

    private final Function<T, ItemStack> iconResolver;

    public FloatingIconRenderer(Function<T, ItemStack> iconResolver) {
        this.iconResolver = iconResolver;
    }

    @Override
    public FloatingIconRenderState createRenderState() {
        return new FloatingIconRenderState();
    }

    @Override
    public void extractRenderState(T blockEntity, FloatingIconRenderState state,
                                    float partialTicks, Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderState.extractBase(blockEntity, state, breakProgress);
        ItemStack icon = iconResolver.apply(blockEntity);
        Level level = blockEntity.getLevel();
        long gameTime = level != null ? level.getGameTime() : 0L;
        state.yRotDegrees = (gameTime + partialTicks) * DEGREES_PER_TICK;
        Minecraft.getInstance().getItemModelResolver()
                .updateForTopItem(state.itemRenderState, icon, ItemDisplayContext.GROUND, level, null, 0);
    }

    @Override
    public void submit(FloatingIconRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        if (state.itemRenderState.isEmpty()) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(0.5, FLOAT_HEIGHT, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees((float) state.yRotDegrees));
        poseStack.scale(SCALE, SCALE, SCALE);
        // outlineColor=0 means "no outline" -- confirmed against the real decompiled
        // EntityRenderState/EntityRenderer source (`outlineColor = appearsGlowing ? ... : 0`, and
        // `hasOutline() = outlineColor != 0`). A real live bug: this was originally -1
        // (0xFFFFFFFF), which isn't "disabled," it's "outline enabled, opaque white" -- rendered the
        // icon as a solid white silhouette with no texture at all instead of a disabled outline.
        state.itemRenderState.submit(poseStack, submitNodeCollector, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }
}
