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
                    //注意：这里不能加 sortOnUpload()。26.2 的 feature 管线在 RenderTypeFeatureRenderer.getOrAddDraw
                    //里发现 sortOnUpload 且拓扑不是 QUADS 时会抛 "Cannot sort draw with LINES"。
                    //半透明混色由 LINES_SNIPPET 自带的 BlendFunction.TRANSLUCENT 提供，与 1.21.11 原版的 .translucent() 等价。
                    .createRenderSetup()
    );
}
