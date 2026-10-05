package com.minecartvisualizer.mixin.client;

import com.minecartvisualizer.*;
import com.minecartvisualizer.config.Colors;
import com.minecartvisualizer.config.MinecartVisualizerConfig;
import com.minecartvisualizer.tracker.*;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.HopperMinecartEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.profiler.Profiler;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.minecartvisualizer.InfoRenderer.getAdaptiveColumns;

@Mixin(WorldRenderer.class)
public abstract class WorldRenderMixin {

    @Inject(
            method = "renderEntity",
            at = @At(
                    value = "TAIL"
            )
    )
    private void renderHopperMinecartInfo(
            Entity entity,
            double cameraX,
            double cameraY,
            double cameraZ,
            float tickDelta,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            CallbackInfo ci
    ) {
        var config = MinecartVisualizerConfig.getInstance();
        if(!(entity instanceof HopperMinecartEntity)){
            return;
        }
        HopperMinecartDataPayload hopperMinecartData = MinecartClientHandler.getFreshHopperMinecartData(entity.getUuid());

        if (!InfoRenderer.shouldRender(entity)) {
            return;
        }

        if (hopperMinecartData == null) return;

        MinecartsGroup group = MinecartClientHandler.getGroup(entity.getUuid());

        boolean isLocked = !hopperMinecartData.enable();

    //漏斗矿车物品栏渲染
        if (config.enableHopperMinecartInventoryDisplay) {
            java.util.function.IntFunction<Integer> getFinalCols = (totalCount) ->
                    config.autoSizeColumns ? getAdaptiveColumns(totalCount) : Math.max(1, config.inventoryCols);

            if (config.mergeStackingMinecartInfo) {
                List<UUID> minecarts = group.getMinecarts();

                //折叠渲染
                if (config.foldInventory) {
                    UUID leaderUuid = group.getLeader();
                    HopperMinecartDataPayload data = MinecartClientHandler.getFreshHopperMinecartData(leaderUuid);
                    if (data != null && !data.items().isEmpty()) {
                        List<ItemStack> filteredItems = InfoRenderer.filterItems(data.items());

                        int finalCols = getFinalCols.apply(filteredItems.size());

                        InfoRenderer.renderInventory(
                                filteredItems, entity, finalCols,
                                cameraX, cameraY, cameraZ, tickDelta, matrices, isLocked
                        );
                    }
                }
                //汇总渲染
                else {
                    List<ItemStack> allItems = new ArrayList<>();
                    for (UUID minecartUuid : minecarts) {
                        HopperMinecartDataPayload data = MinecartClientHandler.getFreshHopperMinecartData(minecartUuid);
                        if (data != null && !data.items().isEmpty()) {
                            allItems.addAll(data.items());
                        }
                    }

                    if (!allItems.isEmpty()) {
                        //应用槽位过滤
                        List<ItemStack> filteredItems = InfoRenderer.filterItems(allItems);
                        int currentSize = filteredItems.size();

                        if (config.maxInventorySlotsToRender == 0 || currentSize <= config.maxInventorySlotsToRender) {

                            int finalCols = getFinalCols.apply(currentSize);

                            InfoRenderer.renderInventory(
                                    filteredItems, entity, finalCols,
                                    cameraX, cameraY, cameraZ, tickDelta, matrices, isLocked
                            );
                        }
                    }
                }
            } else {
                //单矿车渲染
                if (!hopperMinecartData.items().isEmpty()) {
                    List<ItemStack> filteredItems = InfoRenderer.filterItems(hopperMinecartData.items());

                    int finalCols = getFinalCols.apply(filteredItems.size());

                    InfoRenderer.renderInventory(
                            filteredItems, entity, finalCols,
                            cameraX, cameraY, cameraZ, tickDelta, matrices, isLocked
                    );
                }
            }
        }

        if (!isLocked && (config.highlightExtractionTargets || config.renderHopperRanges)){
            //绘制锚点用客户端插值坐标（实体渲染插值开启时跟随才平滑；用服务端坐标会一跳一跳）
            //服务端坐标只用于判定数据是否新鲜/是否权威，不再当作绘制坐标
            Vec3d hopperPos = InfoRenderer.getClientPos(entity, tickDelta);

            boolean hasTargets = false;
            if (config.highlightExtractionTargets){
                //高亮吸取目标（置顶渲染）
                hasTargets = InfoRenderer.queueExtractionTargets(hopperMinecartData, config.extractionTargetBoxScale);
            }

            //渲染吸取范围
            if (!hasTargets && config.renderHopperRanges){
                queueOrRenderHopperRanges(config, entity, hopperPos, cameraX, cameraY, cameraZ, matrices, vertexConsumers);
            }
        }
    }

    @Unique
    private static void queueOrRenderHopperRanges(MinecartVisualizerConfig config, Entity entity, Vec3d hopperPos,
                                                  double cameraX, double cameraY, double cameraZ,
                                                  MatrixStack matrices, VertexConsumerProvider vertexConsumers) {
        Box[] rangeBoxes = InfoRenderer.buildHopperRangeBoxes(entity, hopperPos);
        float rangeScale = config.hopperRangeBoxScale;
        float[] pickupColor = Colors.rgbFloats(config.pickupRangeColor,
                MinecartVisualizerConfig.DEFAULT_PICKUP_RANGE_COLOR.getRGB());
        float[] extractionColor = Colors.rgbFloats(config.extractionRangeColor,
                MinecartVisualizerConfig.DEFAULT_EXTRACTION_RANGE_COLOR.getRGB());

        if (config.hopperVisualOnTop) {
            InfoRenderer.queueWorldBox(rangeBoxes[0], rangeScale, pickupColor);
            InfoRenderer.queueWorldBox(rangeBoxes[1], rangeScale, extractionColor);
        } else {
            InfoRenderer.renderHopperRanges(entity, hopperPos, cameraX, cameraY, cameraZ, matrices, vertexConsumers,
                    pickupColor, extractionColor, rangeScale);
        }
    }

    @Inject(
            method = "render",
            at = @At(
                    value = "HEAD"
            )
    )
    private void beginTopRenderFrame(
            MatrixStack matrices, float tickDelta, long limitTime, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightmapTextureManager lightmapTextureManager, Matrix4f projectionMatrix, CallbackInfo ci
    ) {
        InfoRenderer.beginTopRenderFrame();
    }

    @Inject(
            method = "render",
            at = @At(
                    value = "TAIL"
            )
    )
    private void renderQueuedTopRenderContent(
            MatrixStack matrices, float tickDelta, long limitTime, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightmapTextureManager lightmapTextureManager, Matrix4f projectionMatrix, CallbackInfo ci
    ) {
        if (!InfoRenderer.hasQueuedTopRenderContent()) {
            return;
        }

        //此时的模型视图矩阵已被原版弹出，需要自行设置世界相机变换
        MatrixStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.push();
        modelViewStack.multiplyPositionMatrix(projectionMatrix);
        //关闭深度测试，让线框无视深度绘制在最上层
        RenderSystem.disableDepthTest();
        try {
            InfoRenderer.renderQueuedExtractionTargets();
            InfoRenderer.renderQueuedWorldBoxes();
            //悬浮信息文本开启置顶渲染时也在此绘制
            InfoRenderer.renderQueuedInfoTexts();
        } finally {
            RenderSystem.enableDepthTest();
            modelViewStack.pop();
        }
    }

    @Inject(
            method = "render",
            at = @At(
                    value = "HEAD"
            )
    )
    private void renderTrails(
            MatrixStack matrices, float tickDelta, long limitTime, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightmapTextureManager lightmapTextureManager, Matrix4f projectionMatrix, CallbackInfo ci
    ) {
        var config = MinecartVisualizerConfig.getInstance();
        if (config.trackMinecartTrail){
            VertexConsumerProvider.Immediate consumers = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
            VertexConsumer lineConsumer = consumers.getBuffer(RenderLayer.LINES);

            Vec3d camPos = camera.getPos();
            MatrixStack trailMatrices = new MatrixStack();
            trailMatrices.translate(-camPos.x, -camPos.y, -camPos.z);

            for (HopperMinecartTracker tracker : TrackersManager.getAllTrackers()) {
                InfoRenderer.renderTrail(tracker, trailMatrices, lineConsumer);
            }
        }
    }

    @Inject(
            method = "render",
            at = @At(value = "HEAD")
    )
    private void renderTriggerPoints(
            MatrixStack matrices, float tickDelta, long limitTime, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightmapTextureManager lightmapTextureManager, Matrix4f projectionMatrix, CallbackInfo ci
    ) {
        Map<BlockPos, PointState> trackerPoints = TrackerPointsManager.getPoints();
        if (trackerPoints.isEmpty()) return;

        VertexConsumerProvider.Immediate consumers = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
        VertexConsumer lineConsumer = consumers.getBuffer(RenderLayer.LINES);

        Vec3d camPos = camera.getPos();
        MatrixStack pointMatrices = new MatrixStack();
        pointMatrices.translate(-camPos.x, -camPos.y, -camPos.z);

        for (Map.Entry<BlockPos, PointState> entry : trackerPoints.entrySet()) {
            BlockPos pos = entry.getKey();

            InfoRenderer.drawTrackerPointBox(
                    pointMatrices,
                    lineConsumer,
                    entry.getValue().getColor(),
                    pos,
                    entry.getValue().isActive()
            );

        }
    }
}
