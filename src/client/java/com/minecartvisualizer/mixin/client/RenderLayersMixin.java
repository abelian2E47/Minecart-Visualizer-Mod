package com.minecartvisualizer.mixin.client;

import com.minecartvisualizer.InfoRenderer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.block.BlockState;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.*;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;

import java.util.function.Function;

import static net.minecraft.client.render.RenderLayers.getBlockLayer;


@Mixin(RenderLayers.class)
public class RenderLayersMixin {


    @Unique
    private static final Function<Identifier, RenderLayer> CUSTOM_ITEM_TRANSLUCENT_CULL_ON_TOP = Util.memoize(
            texture -> {
                RenderLayer.MultiPhaseParameters params = RenderLayer.MultiPhaseParameters.builder()
                        .texture(new RenderPhase.Texture(texture, false))
                        .lightmap(RenderPhase.ENABLE_LIGHTMAP)
                        .overlay(RenderPhase.ENABLE_OVERLAY_COLOR)
                        .layering(RenderPhase.VIEW_OFFSET_Z_LAYERING)
                        .target(RenderPhase.MAIN_TARGET)
                        .build(true);

                RenderPipeline pipeline = RenderPipelines.RENDERTYPE_TEXT_SEETHROUGH;
                return RenderLayer.of(
                        "custom_item_translucent_cull_on_top",
                        1536,
                        false,
                        false,
                        pipeline,
                        params
                );
            }
    );
    @Unique
    private static final Function<Identifier, RenderLayer> CUSTOM_ENTITY_CUTOUT_ON_TOP = Util.memoize(
            texture -> {
                RenderLayer.MultiPhaseParameters params = RenderLayer.MultiPhaseParameters.builder()
                        .texture(new RenderPhase.Texture(texture, false))
                        .lightmap(RenderPhase.ENABLE_LIGHTMAP)
                        .overlay(RenderPhase.ENABLE_OVERLAY_COLOR)
                        .layering(RenderPhase.VIEW_OFFSET_Z_LAYERING)
                        .target(RenderPhase.MAIN_TARGET)
                        .build(true);

                RenderPipeline pipeline = RenderPipelines.RENDERTYPE_TEXT_SEETHROUGH;
                return RenderLayer.of(
                        "entity_cutout",
                        1536,
                        false,
                        false,
                        pipeline,
                        params
                );
            }
    );

    @Inject(
            method = "getItemLayer",
            at = @At(
                    value = "HEAD"
            ),
            cancellable = true)
    private static void getCustomLayer(ItemStack stack, CallbackInfoReturnable<RenderLayer> cir){
        if (InfoRenderer.getCustomRenderLayer){
            RenderLayer customRenderLayer = CUSTOM_ITEM_TRANSLUCENT_CULL_ON_TOP.apply(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
            if (!(stack.getItem() instanceof BlockItem)) {
                cir.setReturnValue(customRenderLayer);
            }
        }
    }

    @Inject(
            method = "getEntityBlockLayer",
            at = @At(
                    value = "HEAD"
            ),
            cancellable = true)
    private static void getCustomLayer(BlockState state, CallbackInfoReturnable<RenderLayer> cir){
        if (InfoRenderer.getCustomRenderLayer){
            BlockRenderLayer renderLayer = getBlockLayer(state);
            RenderLayer translucentRenderLayer = CUSTOM_ITEM_TRANSLUCENT_CULL_ON_TOP.apply(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
            RenderLayer cutoutRenderLayer = CUSTOM_ENTITY_CUTOUT_ON_TOP.apply(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
            cir.setReturnValue(renderLayer == BlockRenderLayer.TRANSLUCENT ? translucentRenderLayer : cutoutRenderLayer);
        }
    }

}
