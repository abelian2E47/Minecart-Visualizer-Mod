package com.minecartvisualizer;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;

import java.util.OptionalDouble;

public final class CustomRenderLayers {

    private CustomRenderLayers() {
    }

    public static final RenderLayer LINES_ON_TOP = RenderLayer.of(
            "minecartvisualizer_lines_on_top",
            1536,
            false,
            false,
            RenderPipelines.LINES,
            RenderLayer.MultiPhaseParameters.builder()
                    .lineWidth(new RenderPhase.LineWidth(OptionalDouble.empty()))
                    .layering(RenderPhase.VIEW_OFFSET_Z_LAYERING)
                    .target(RenderPhase.MAIN_TARGET)
                    .build(false)
    );
}
