package com.minecartvisualizer.mixin.client;

import com.minecartvisualizer.*;
import com.minecartvisualizer.config.MinecartVisualizerConfig;
import com.minecartvisualizer.tracker.*;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.HopperMinecartEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.profiler.Profiler;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
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
        HopperMinecartDataPayload hopperMinecartData = MinecartClientHandler.getHopperMinecartData(entity.getUuid());

        if (!InfoRenderer.shouldRender(entity)) {
            if (MinecartVisualizerClient.uuid != null && entity.getUuid().equals(MinecartVisualizerClient.uuid)) {
                hopperMinecartData = MinecartClientHandler.getHopperMinecartData(MinecartVisualizerClient.uuid);
            } else return;
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
                    UUID leaderUuid = minecarts.getFirst();
                    HopperMinecartDataPayload data = MinecartClientHandler.getHopperMinecartData(leaderUuid);
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
                        HopperMinecartDataPayload data = MinecartClientHandler.getHopperMinecartData(minecartUuid);
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

        if (!isLocked && config.highlightExtractionTargets){
            //高亮吸取目标
            boolean hasTargets = InfoRenderer.highlightExtractionTargets(entity, cameraX, cameraY, cameraZ, matrices, vertexConsumers);
            //渲染吸取范围
            if (!hasTargets && config.renderHopperRanges){
                InfoRenderer.renderHopperRanges(entity, cameraX, cameraY, cameraZ, matrices, vertexConsumers);
            }
        }
    }

    @Inject(
            method = "renderMain",
            at = @At(
                    value = "TAIL"
            )
    )
    private void renderTrails(
            FrameGraphBuilder frameGraphBuilder, Frustum frustum, Camera camera, Matrix4f positionMatrix, GpuBufferSlice fog, boolean renderBlockOutline, boolean renderEntityOutline, RenderTickCounter tickCounter, Profiler profiler, CallbackInfo ci
    ) {
        var config = MinecartVisualizerConfig.getInstance();
        if (config.trackMinecartTrail){
            VertexConsumerProvider.Immediate consumers = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
            VertexConsumer lineConsumer = consumers.getBuffer(RenderLayer.LINES);

            Vec3d camPos = camera.getPos();
            MatrixStack matrices = new MatrixStack();
            matrices.translate(-camPos.x, -camPos.y, -camPos.z);

            for (HopperMinecartTracker tracker : TrackersManager.getAllTrackers()) {
                InfoRenderer.renderTrail(tracker, matrices, lineConsumer);
            }
        }
    }

    @Inject(
            method = "renderMain",
            at = @At(value = "TAIL")
    )
    private void renderTriggerPoints(
            FrameGraphBuilder frameGraphBuilder, Frustum frustum, Camera camera, Matrix4f positionMatrix, GpuBufferSlice fog, boolean renderBlockOutline, boolean renderEntityOutline, RenderTickCounter tickCounter, Profiler profiler, CallbackInfo ci
    ) {
        Map<BlockPos, PointState> trackerPoints = TrackerPointsManager.getPoints();
        if (trackerPoints.isEmpty()) return;

        VertexConsumerProvider.Immediate consumers = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
        VertexConsumer lineConsumer = consumers.getBuffer(RenderLayer.LINES);

        Vec3d camPos = camera.getPos();
        MatrixStack matrices = new MatrixStack();
        matrices.translate(-camPos.x, -camPos.y, -camPos.z);

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
}
