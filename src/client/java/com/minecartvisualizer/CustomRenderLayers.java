package com.minecartvisualizer;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;

import java.util.OptionalDouble;

/** 本模组自建的渲染层。 */
public final class CustomRenderLayers {

    private CustomRenderLayers() {
    }

    /**
     * 与 {@link RenderLayer#LINES} 参数相同，但目标是主帧缓冲（{@link RenderPhase#MAIN_TARGET}）。
     *
     * <p>{@code RenderLayer.LINES} 的目标是 item_entity 帧缓冲，而该帧缓冲早在
     * {@code WorldRenderer#render} 内部就已被合成进主帧缓冲，因此在 {@code render} 末尾
     * 做置顶绘制时必须换掉这个目标。</p>
     *
     * <p>1.20.x 的 {@code MAIN_TARGET} 本身不做任何事（不切换帧缓冲），所以调用方需要在
     * 绘制前自行切回主帧缓冲。</p>
     */
    public static final RenderLayer LINES_ON_TOP = RenderLayer.of(
            "minecartvisualizer_lines_on_top",
            VertexFormats.LINES,
            VertexFormat.DrawMode.LINES,
            1536,
            RenderLayer.MultiPhaseParameters.builder()
                    .program(RenderPhase.LINES_PROGRAM)
                    .lineWidth(new RenderPhase.LineWidth(OptionalDouble.empty()))
                    .layering(RenderPhase.VIEW_OFFSET_Z_LAYERING)
                    .transparency(RenderPhase.TRANSLUCENT_TRANSPARENCY)
                    .target(RenderPhase.MAIN_TARGET)
                    .writeMaskState(RenderPhase.ALL_MASK)
                    .cull(RenderPhase.DISABLE_CULLING)
                    .build(false)
    );
}