package com.minecartvisualizer;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.*;
import net.minecraft.client.texture.SpriteAtlasTexture;


public class CustomRenderLayers {

    public static final RenderLayer CUSTOM_ITEM = RenderLayer.of(
            "custom_item_on_top",
            RenderSetup.builder(RenderPipelines.RENDERTYPE_TEXT_SEETHROUGH)
                    .texture("Sampler0", SpriteAtlasTexture.ITEMS_ATLAS_TEXTURE)
                    .useOverlay()
                    .outputTarget(OutputTarget.ITEM_ENTITY_TARGET)
                    .translucent()
                    .build()
    );

    public static final RenderLayer CUSTOM_BLOCK = RenderLayer.of(
            "custom_block_on_top",
            RenderSetup.builder(RenderPipelines.RENDERTYPE_TEXT_SEETHROUGH)
                    .texture("Sampler0", SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE)
                    .useOverlay()
                    .outputTarget(OutputTarget.ITEM_ENTITY_TARGET)
                    .translucent()
                    .build()
    );

    public static final RenderLayer CUSTOM_BACKGROUND = RenderLayer.of(
            "custom_gui_background",
            RenderSetup.builder(RenderPipelines.GUI)
                    .layeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .translucent()
                    .outputTarget(OutputTarget.MAIN_TARGET)
                    .build()
    );

    public static final RenderLayer CUSTOM_LINES = RenderLayer.of(
            "custom_lines",
            RenderSetup.builder(RenderPipelines.LINES)
                    .outputTarget(OutputTarget.MAIN_TARGET)
                    .layeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .translucent()
                    .build()
    );
}
