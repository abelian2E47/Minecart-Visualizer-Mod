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
            InfoRenderer.renderQueuedTargetBoxes();
            InfoRenderer.renderQueuedInventories();
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
