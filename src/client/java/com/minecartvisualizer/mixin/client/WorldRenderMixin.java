package com.minecartvisualizer.mixin.client;

import com.minecartvisualizer.InfoRenderer;
import com.minecartvisualizer.config.MinecartVisualizerConfig;
import com.minecartvisualizer.tracker.HopperMinecartTracker;
import com.minecartvisualizer.tracker.PointState;
import com.minecartvisualizer.tracker.TrackerPointsManager;
import com.minecartvisualizer.tracker.TrackersManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(LevelRenderer.class)
public abstract class WorldRenderMixin {

    @Inject(
            method = "renderLevel",
            at = @At("HEAD")
    )
    private void clearQueuedTopRenderContent(
            GraphicsResourceAllocator allocator, DeltaTracker tickCounter, boolean renderBlockOutline, CameraRenderState cameraState,
            Matrix4fc modelViewMatrix, GpuBufferSlice fogBuffer, Vector4f fogColor, boolean renderSky,
            ChunkSectionsToRender chunkSectionsToRender, CallbackInfo ci
    ) {
        InfoRenderer.beginTopRenderFrame();
    }
    @Inject(
            method = "renderLevel",
            at = @At("TAIL")
    )
    private void renderQueuedTopRenderContent(
            GraphicsResourceAllocator allocator, DeltaTracker tickCounter, boolean renderBlockOutline, CameraRenderState cameraState,
            Matrix4fc modelViewMatrix, GpuBufferSlice fogBuffer, Vector4f fogColor, boolean renderSky,
            ChunkSectionsToRender chunkSectionsToRender, CallbackInfo ci
    ) {
        if (!InfoRenderer.hasQueuedTopRenderContent()) {
            return;
        }

        var modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.mul(modelViewMatrix);
        try {
            Minecraft client = Minecraft.getInstance();
            RenderSystem.getDevice().createCommandEncoder().clearDepthTexture(client.getMainRenderTarget().getDepthTexture(), 1.0);
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
            method = "addMainPass",
            at = @At(value = "TAIL")
    )
    private void renderTrials(
            FrameGraphBuilder frameGraphBuilder, Frustum frustum, Matrix4fc posMatrix, GpuBufferSlice fogBuffer, boolean renderBlockOutline, LevelRenderState state, DeltaTracker tickCounter, ProfilerFiller profiler, ChunkSectionsToRender chunkSectionsToRender, CallbackInfo ci
    ) {
        var config = MinecartVisualizerConfig.getInstance();
        boolean hasTrails = config.trackMinecartTrail && !TrackersManager.getAllTrackers().isEmpty();
        Map<BlockPos, PointState> trackerPoints = TrackerPointsManager.getPoints();
        boolean hasPoints = !trackerPoints.isEmpty();
        if (!hasTrails && !hasPoints) return;

        var customPass = frameGraphBuilder.addPass("minecart_custom_overlay");
        //帧图只会执行"根 pass"（持有外部资源 handle 的 pass）及其依赖，未被依赖的 pass 会被剔除；
        //本 pass 既不读写任何资源也不被他人依赖，必须显式关闭剔除，否则 executes 里的绘制永远不会执行（轨迹线与追踪点框都不显示）。
        customPass.disableCulling();
        customPass.executes(() -> {
            MultiBufferSource.BufferSource consumers = Minecraft.getInstance().renderBuffers().bufferSource();
            VertexConsumer lineConsumer = consumers.getBuffer(RenderTypes.LINES);

            Vec3 camPos = state.cameraRenderState.pos;
            PoseStack matrices = new PoseStack();
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

            consumers.endLastBatch();
            consumers.endBatch(RenderTypes.LINES);
        });
    }
}
