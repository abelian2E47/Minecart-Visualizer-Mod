package com.minecartvisualizer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.feature.BlockModelFeatureRenderer;
import net.minecraft.client.renderer.feature.CustomFeatureRenderer;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.feature.ShapeOutlineFeatureRenderer;
import net.minecraft.client.renderer.feature.TextFeatureRenderer;
import net.minecraft.client.renderer.feature.phase.SimpleFeatureRenderPhase;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;

import java.util.List;

public final class OverlaySubmit extends SubmitNodeCollection implements SubmitNodeCollector {

    private final SimpleFeatureRenderPhase phase;

    private OverlaySubmit(SimpleFeatureRenderPhase phase) {
        this.phase = phase;
    }

    @Override
    public OrderedSubmitNodeCollector order(int order) {
        return this;
    }

    public static SubmitNodeCollector of(SubmitNodeCollector levelCollector, boolean onTop) {
        if (levelCollector instanceof SubmitNodeStorage storage) {
            SubmitNodeCollection collection = storage.order(0);
            return new OverlaySubmit(onTop ? collection.alwaysOnTop : collection.afterTerrain);
        }

        return levelCollector;
    }

    @Override
    public void submitText(PoseStack poseStack, float x, float y, FormattedCharSequence string, boolean dropShadow,
                           Font.DisplayMode displayMode, int lightCoords, int color, int backgroundColor, int outlineColor) {
        this.phase.submit(new TextFeatureRenderer.Submit(new Matrix4f(poseStack.last().pose()), x, y, string,
                dropShadow, displayMode, lightCoords, color, backgroundColor, outlineColor));
    }

    @Override
    public void submitShapeOutline(PoseStack poseStack, VoxelShape shape, RenderType renderType, int color, float width,
                                   boolean afterTerrain) {
        this.phase.submit(new ShapeOutlineFeatureRenderer.Submit(poseStack.last().copy(), shape, renderType, color, width));
    }

    @Override
    public void submitCustomGeometry(PoseStack poseStack, RenderType renderType,
                                     SubmitNodeCollector.CustomGeometryRenderer customGeometryRenderer) {
        this.phase.submit(new CustomFeatureRenderer.Submit(poseStack.last().copy(), renderType, customGeometryRenderer));
    }

    @Override
    public void submitItem(PoseStack poseStack, ItemDisplayContext displayContext, int lightCoords, int overlayCoords,
                           int outlineColor, int[] tintLayers, List<BakedQuad> quads, ItemStackRenderState.FoilType foilType) {
        this.phase.submit(new ItemFeatureRenderer.Submit(poseStack.last().copy(), displayContext, lightCoords,
                overlayCoords, 0, tintLayers, quads, foilType));
    }

    //物品的特殊模型渲染器（箱子、潜影盒、旗帜、头颅……）走的是 submitModel，同样要落到目标阶段，
    //否则会进入本收集器里无人读取的自然阶段而被静默丢弃。
    @Override
    public <S> void submitModel(Model<? super S> model, S state, PoseStack poseStack, RenderType renderType,
                                int lightCoords, int overlayCoords, int tintedColor, TextureAtlasSprite sprite,
                                int outlineColor, ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
        PoseStack.Pose pose = poseStack.last().copy();
        if (!renderType.isOutline()) {
            this.phase.submit(new ModelFeatureRenderer.Submit<>(renderType, pose, model, state, lightCoords,
                    overlayCoords, tintedColor, sprite, null));
        }

        if (outlineColor != 0) {
            RenderType outlineType = renderType.isOutline() ? renderType : renderType.outline().orElse(null);
            if (outlineType != null) {
                this.phase.submit(new ModelFeatureRenderer.Submit<>(outlineType, pose, model, state, 15728880,
                        OverlayTexture.NO_OVERLAY, outlineColor, sprite, null));
            }
        }

        if (crumblingOverlay != null && renderType.affectsCrumbling()) {
            RenderType crumblingType = ModelBakery.DESTROY_TYPES.get(crumblingOverlay.progress());
            this.phase.submit(new ModelFeatureRenderer.Submit<>(crumblingType, pose, model, state, lightCoords,
                    overlayCoords, tintedColor, null, crumblingOverlay.cameraPose()));
        }
    }

    @Override
    public void submitBlockModel(PoseStack poseStack, RenderType renderType, List<BlockStateModelPart> modelParts,
                                 int[] tintLayers, int lightCoords, int overlayCoords, int outlineColor) {
        this.phase.submit(new BlockModelFeatureRenderer.Submit(poseStack.last().copy(), renderType, modelParts,
                tintLayers, lightCoords, overlayCoords, -1, null));
    }
}
