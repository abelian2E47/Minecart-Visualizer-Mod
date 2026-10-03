package com.minecartvisualizer;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;

import java.util.OptionalDouble;

/** 本模组自建的渲染层。 */
public final class CustomRenderLayers {

    private CustomRenderLayers() {
    }

    /**
     * 与 {@link RenderLayer#LINES} 参数相同，但绘制目标是主帧缓冲（{@link RenderPhase#MAIN_TARGET}）。
     *
     * <p>{@code RenderLayer.LINES} 的目标是 item_entity 帧缓冲，而该帧缓冲早在
     * {@code WorldRenderer#render} 内部就已被合成进主帧缓冲，因此想在
     * {@code render} 末尾做置顶绘制时必须显式指定主帧缓冲。</p>
     */
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
