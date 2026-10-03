package com.minecartvisualizer.mixin.client;

import com.minecartvisualizer.InfoRenderer;
import com.minecartvisualizer.config.MinecartVisualizerConfig;
import com.minecartvisualizer.tracker.HopperMinecartTracker;
import com.minecartvisualizer.tracker.PointState;
import com.minecartvisualizer.tracker.TrackerPointsManager;
import com.minecartvisualizer.tracker.TrackersManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.render.state.WorldRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.util.ObjectAllocator;
import net.minecraft.util.profiler.Profiler;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(WorldRenderer.class)
public abstract class WorldRenderMixin {

    @Inject(
            method = "render",
            at = @At("HEAD")
    )
    private void clearQueuedTopRenderContent(
            ObjectAllocator allocator, RenderTickCounter tickCounter, boolean renderBlockOutline, Camera camera,
            Matrix4f positionMatrix, Matrix4f basicProjectionMatrix, Matrix4f projectionMatrix,
            GpuBufferSlice fogBuffer, Vector4f fogColor, boolean renderSky, CallbackInfo ci
    ) {
        InfoRenderer.beginTopRenderFrame();
    }
    @Inject(
            method = "render",
            at = @At("TAIL")
    )
    private void renderQueuedTopRenderContent(
            ObjectAllocator allocator, RenderTickCounter tickCounter, boolean renderBlockOutline, Camera camera,
            Matrix4f positionMatrix, Matrix4f basicProjectionMatrix, Matrix4f projectionMatrix,
            GpuBufferSlice fogBuffer, Vector4f fogColor, boolean renderSky, CallbackInfo ci
    ) {
        if (!InfoRenderer.hasQueuedTopRenderContent()) {
            return;
        }

        var modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.mul(positionMatrix);
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            RenderSystem.getDevice().createCommandEncoder().clearDepthTexture(client.getFramebuffer().getDepthAttachment(), 1.0);
            //三者互不排斥，各自在没有内容时自己返回。
            //（旧代码是"有吸取目标就整帧不画物品栏和范围框"，任何一台矿车有目标都会让全场面板闪烁消失。）
            InfoRenderer.renderQueuedExtractionTargets();
            InfoRenderer.renderQueuedWorldBoxes();
            InfoRenderer.renderQueuedInventories();
            //悬浮信息文本开启置顶渲染时也在此绘制（面板之后，压在面板之上）
            InfoRenderer.renderQueuedInfoTexts();
        } finally {
            modelViewStack.popMatrix();
        }
    }


    @Inject(
            method = "renderMain",
            at = @At(value = "TAIL")
    )
    private void renderTrials(
            FrameGraphBuilder frameGraphBuilder, Frustum frustum, Matrix4f posMatrix, GpuBufferSlice fogBuffer, boolean renderBlockOutline, WorldRenderState state, RenderTickCounter tickCounter, Profiler profiler, CallbackInfo ci
    ) {
        var config = MinecartVisualizerConfig.getInstance();
        boolean hasTrails = config.trackMinecartTrail && !TrackersManager.getAllTrackers().isEmpty();
        Map<BlockPos, PointState> trackerPoints = TrackerPointsManager.getPoints();
        boolean hasPoints = !trackerPoints.isEmpty();
        if (!hasTrails && !hasPoints) return;

        var customPass = frameGraphBuilder.createPass("minecart_custom_overlay");
        //帧图只会执行"根 pass"（拥有输出资源的 pass）及其依赖，未被任何 pass 依赖的 pass 会被剔除；
        //本 pass 既不产出资源也不被他人依赖，必须显式标记，否则 setRenderer 里的绘制永远不会执行（轨迹线与追踪点框都不显示）。
        customPass.markToBeVisited();
        customPass.setRenderer(() -> {
            VertexConsumerProvider.Immediate consumers = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
            VertexConsumer lineConsumer = consumers.getBuffer(RenderLayers.LINES);

            Vec3d camPos = state.cameraRenderState.pos;
            MatrixStack matrices = new MatrixStack();
            matrices.translate(-camPos.x, -camPos.y, -camPos.z);

            if (hasTrails) {
                for (HopperMinecartTracker tracker : TrackersManager.getAllTrackers()) {
                    InfoRenderer.renderTrail(tracker, matrices, lineConsumer);
                }
            }

            if (hasPoints) {
                for (Map.Entry<BlockPos, PointState> entry : trackerPoints.entrySet()) {
                    BlockPos pos = entry.getKey();
                    InfoRenderer.drawTrackerPointBox(
                            matrices,
                            lineConsumer,
                            entry.getValue().getColor(),
                            pos,
                            entry.getValue().isActive()
                    );
                }
            }

            consumers.drawCurrentLayer();
            consumers.draw(RenderLayers.LINES);
        });
    }
}
