package com.minecartvisualizer.mixin.client;

import com.minecartvisualizer.InfoRenderer;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.*;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.TriState;
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
import static net.minecraft.client.render.RenderPhase.*;


@Mixin(RenderLayers.class)
public class RenderLayersMixin {


    @Unique
    private static final Function<Identifier, RenderLayer> CUSTOM_ITEM_TRANSLUCENT_CULL_ON_TOP = Util.memoize(
            texture -> {
                RenderLayer.MultiPhaseParameters multiPhaseParameters = RenderLayer.MultiPhaseParameters.builder()
                        .program(ITEM_ENTITY_TRANSLUCENT_CULL_PROGRAM)
                        .texture(new RenderPhase.Texture(texture, TriState.FALSE, false))
                        .transparency(TRANSLUCENT_TRANSPARENCY)
                        .target(ITEM_ENTITY_TARGET)
                        .lightmap(ENABLE_LIGHTMAP)
                        .overlay(ENABLE_OVERLAY_COLOR)
                        .writeMaskState(ALL_MASK)
                        .depthTest(ALWAYS_DEPTH_TEST)
                        .build(true);
                return RenderLayer.of(
                        "custom_item_translucent_cull_on_top",
                        VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL,
                        VertexFormat.DrawMode.QUADS,
                        1536,
                        true,
                        true,
                        multiPhaseParameters
                );
            }
    );

    @Unique
    private static final Function<Identifier, RenderLayer> CUSTOM_ENTITY_CUTOUT_ON_TOP = Util.memoize(
            texture -> {
                RenderLayer.MultiPhaseParameters multiPhaseParameters = RenderLayer.MultiPhaseParameters.builder()
                        .program(ENTITY_CUTOUT_PROGRAM)
                        .texture(new RenderPhase.Texture(texture, TriState.FALSE, false))
                        .transparency(NO_TRANSPARENCY)
                        .lightmap(ENABLE_LIGHTMAP)
                        .overlay(ENABLE_OVERLAY_COLOR)
                        .depthTest(ALWAYS_DEPTH_TEST)
                        .build(true);
                return RenderLayer.of(
                        "entity_cutout",
                        VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL,
                        VertexFormat.DrawMode.QUADS,
                        1536,
                        true,
                        false,
                        multiPhaseParameters);
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
            RenderLayer renderLayer = getBlockLayer(state);
            RenderLayer translucentRenderLayer = CUSTOM_ITEM_TRANSLUCENT_CULL_ON_TOP.apply(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
            RenderLayer cutoutRenderLayer = CUSTOM_ENTITY_CUTOUT_ON_TOP.apply(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
            cir.setReturnValue(renderLayer == RenderLayer.getTranslucent() ?translucentRenderLayer : cutoutRenderLayer);
        }
    }

}
