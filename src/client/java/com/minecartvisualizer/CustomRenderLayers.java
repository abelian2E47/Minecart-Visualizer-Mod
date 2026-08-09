package com.minecartvisualizer;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.*;


public class CustomRenderLayers {


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
