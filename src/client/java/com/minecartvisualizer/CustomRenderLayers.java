package com.minecartvisualizer;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;


public class CustomRenderLayers {


    public static final RenderType CUSTOM_BACKGROUND = RenderType.create(
            "custom_gui_background",
            RenderSetup.builder(RenderPipelines.GUI)
                    .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .sortOnUpload()
                    .setOutputTarget(OutputTarget.MAIN_TARGET)
                    .createRenderSetup()
    );

    public static final RenderType CUSTOM_LINES = RenderType.create(
            "custom_lines",
            RenderSetup.builder(RenderPipelines.LINES)
                    .setOutputTarget(OutputTarget.MAIN_TARGET)
                    .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .sortOnUpload()
                    .createRenderSetup()
    );
}
