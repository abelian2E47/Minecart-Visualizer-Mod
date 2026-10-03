package com.minecartvisualizer.mixin.client;

import com.minecartvisualizer.InfoRenderer;
import com.minecartvisualizer.OverlaySubmit;
import com.minecartvisualizer.config.MinecartVisualizerConfig;
import com.minecartvisualizer.tracker.HopperMinecartTracker;
import com.minecartvisualizer.tracker.PointState;
import com.minecartvisualizer.tracker.TrackerPointsManager;
import com.minecartvisualizer.tracker.TrackersManager;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(LevelRenderer.class)
public abstract class WorldRenderMixin {

    //关卡开始收集本帧提交内容前，清空上一帧排队的置顶渲染内容
    @Inject(
            method = "submitFeatures",
            at = @At("HEAD")
    )
    private void clearQueuedTopRenderContent(LevelRenderState state, SubmitNodeCollector collector, boolean renderOutline, CallbackInfo ci) {
        InfoRenderer.beginTopRenderFrame();
    }

    //实体提交完毕后（原版随后 prepareFrame 并执行帧图），把本帧排队的内容提交进关卡提交存储。
    //26.2 的绘制全部走提交管线：这里只负责提交，实际绘制仍由原版各阶段完成。
    @Inject(
            method = "submitFeatures",
            at = @At("TAIL")
    )
    private void submitQueuedTopRenderContent(LevelRenderState state, SubmitNodeCollector collector, boolean renderOutline, CallbackInfo ci) {
        //轨迹线与追踪点：地形之后绘制且参与深度测试
        renderTrailsAndPoints(state, OverlaySubmit.of(collector, false));

        if (!InfoRenderer.hasQueuedTopRenderContent()) {
            return;
        }

        //三者互不排斥，各自在没有内容时自己返回。
        //（旧代码是"有吸取目标就整帧不画物品栏和范围框"，任何一台矿车有目标都会让全场面板闪烁消失。）
        SubmitNodeCollector onTop = OverlaySubmit.of(collector, true);
        InfoRenderer.renderQueuedExtractionTargets(onTop);
        InfoRenderer.renderQueuedWorldBoxes(onTop);
        InfoRenderer.renderQueuedInventories(onTop);
        //悬浮信息文本开启置顶渲染时也在此提交（面板之后，压在面板之上）
        InfoRenderer.renderQueuedInfoTexts(onTop);
    }

    @Unique
    private static void renderTrailsAndPoints(LevelRenderState state, SubmitNodeCollector collector) {
        var config = MinecartVisualizerConfig.getInstance();
        boolean hasTrails = config.trackMinecartTrail && !TrackersManager.getAllTrackers().isEmpty();
        Map<BlockPos, PointState> trackerPoints = TrackerPointsManager.getPoints();
        boolean hasPoints = !trackerPoints.isEmpty();
        if (!hasTrails && !hasPoints) return;

        Vec3 camPos = state.cameraRenderState.pos;
        PoseStack matrices = new PoseStack();
        matrices.translate(-camPos.x, -camPos.y, -camPos.z);

        if (hasTrails) {
            for (HopperMinecartTracker tracker : TrackersManager.getAllTrackers()) {
                InfoRenderer.renderTrail(tracker, matrices, collector);
            }
        }

        if (hasPoints) {
            for (Map.Entry<BlockPos, PointState> entry : trackerPoints.entrySet()) {
                BlockPos pos = entry.getKey();
                InfoRenderer.drawTrackerPointBox(
                        matrices,
                        collector,
                        entry.getValue().getColor(),
                        pos,
                        entry.getValue().isActive()
                );
            }
        }
    }
}
