package com.minecartvisualizer.mixin.client;
import com.minecartvisualizer.*;
import com.minecartvisualizer.config.Colors;
import com.minecartvisualizer.config.MinecartVisualizerConfig;
import com.minecartvisualizer.tracker.HopperMinecartTracker;
import com.minecartvisualizer.tracker.TrackerColor;
import com.minecartvisualizer.tracker.TrackersManager;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.ArrayList;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecartContainer;
import net.minecraft.world.entity.vehicle.minecart.MinecartHopper;
import net.minecraft.world.entity.vehicle.minecart.MinecartTNT;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static com.minecartvisualizer.InfoRenderer.getAdaptiveColumns;

@Mixin(EntityRenderer.class)
public class EntityRendererMixin<T extends Entity, S extends EntityRenderState> {
    @Unique
    private final Map<S, T> stateToEntity = new WeakHashMap<>();

    @Inject(
            method = "extractRenderState",
            at = @At("TAIL")
    )
    private void captureEntity(T entity, S state, float tickProgress, CallbackInfo ci) {
        stateToEntity.put(state, entity);
    }

    @Inject(
            method = "submit",
            at = @At("HEAD")
    )
    private void renderMinecartInfo(
            S renderState, PoseStack matrices, SubmitNodeCollector queue, CameraRenderState cameraState, CallbackInfo ci
    ) {
        T entity = stateToEntity.get(renderState);
        if (entity == null) return;

        if (!(entity instanceof AbstractMinecart)) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        Vec3 cameraPos = client.gameRenderer.getMainCamera().position();
        double cameraX = cameraPos.x;
        double cameraY = cameraPos.y;
        double cameraZ = cameraPos.z;
        MultiBufferSource.BufferSource vertexConsumers = client.renderBuffers().bufferSource();

        //绘制锚点用客户端插值坐标（renderState.x/y/z 已含实体渲染插值），物品栏与范围框跟随才平滑；
        //服务端坐标只用于"数据是否新鲜、是否权威"的判定，不再当作绘制坐标（会一跳一跳）
        Vec3 anchor = new Vec3(renderState.x, renderState.y, renderState.z);

        if (entity instanceof MinecartHopper) {
            HopperMinecartDataPayload hopperMinecartData = MinecartClientHandler.getFreshHopperMinecartData(entity.getUUID());
            if (hopperMinecartData != null && InfoRenderer.shouldRender(entity)) {
                MinecartsGroup group = MinecartClientHandler.getGroup(entity.getUUID());
                boolean isLocked = !hopperMinecartData.enable();
                renderHopperMinecartInfo(hopperMinecartData, entity, group, isLocked, anchor, cameraX, cameraY, cameraZ, matrices, vertexConsumers);
            }
        }

        MinecartsGroup group = MinecartClientHandler.getGroup(entity.getUUID());
        renderTextInfo(entity, group, matrices, vertexConsumers);
    }

    @Unique
    private void renderHopperMinecartInfo(HopperMinecartDataPayload hopperMinecartData,T entity, MinecartsGroup group,
                                          boolean isLocked, Vec3 anchor,
                                          double cameraX, double cameraY, double cameraZ,
                                          PoseStack matrices, MultiBufferSource.BufferSource vertexConsumers) {
        var config = MinecartVisualizerConfig.getInstance();
        if (config.enableHopperMinecartInventoryDisplay) {
            int slotsPerMinecart = 0;
            for (boolean enabled : config.hopperSlotFilter) if (enabled) slotsPerMinecart++;

            java.util.function.IntFunction<Integer> getFinalCols = (totalCount) ->
                    config.autoSizeColumns ? getAdaptiveColumns(totalCount) : Math.max(1, config.inventoryCols);
            if (config.mergeStackingMinecartInfo && group != null) {
                List<UUID> minecarts = group.getMinecarts();
                int totalSlots = config.foldInventory ? slotsPerMinecart : minecarts.size() * slotsPerMinecart;
                if (config.foldInventory) {
                    UUID leaderUuid = minecarts.getFirst();
                    HopperMinecartDataPayload data = MinecartClientHandler.getFreshHopperMinecartData(leaderUuid);
                    if (data != null) {
                        List<ItemStack> filteredItems = InfoRenderer.filterItems(data.items());
                        int finalCols = getFinalCols.apply(totalSlots);
                        InfoRenderer.queueInventory(
                                filteredItems, entity.level(),
                                anchor.x, anchor.y, anchor.z, totalSlots, finalCols, isLocked
                        );
                    }
                } else {
                    List<ItemStack> allItems = new ArrayList<>();
                    for (UUID minecartUuid : minecarts) {
                        HopperMinecartDataPayload data = MinecartClientHandler.getFreshHopperMinecartData(minecartUuid);
                        if (data != null) {
                            allItems.addAll(data.items());
                        }
                    }
                    if (config.maxInventorySlotsToRender == 0 || totalSlots <= config.maxInventorySlotsToRender) {
                        List<ItemStack> filteredItems = InfoRenderer.filterItems(allItems);
                        int finalCols = getFinalCols.apply(totalSlots);
                        InfoRenderer.queueInventory(
                                filteredItems, entity.level(),
                                anchor.x, anchor.y, anchor.z, totalSlots, finalCols, isLocked
                        );
                    }
                }
            } else {
                int totalSlots = slotsPerMinecart;
                List<ItemStack> filteredItems = InfoRenderer.filterItems(hopperMinecartData.items());
                int finalCols = getFinalCols.apply(totalSlots);
                InfoRenderer.queueInventory(
                        filteredItems, entity.level(),
                        anchor.x, anchor.y, anchor.z, totalSlots, finalCols, isLocked
                );
            }
        }

        //绘制吸取范围框
        if (!isLocked && (config.highlightExtractionTargets || config.renderHopperRanges)) {
            boolean hasTarget = false;
            if (config.highlightExtractionTargets) {
                hasTarget = InfoRenderer.queueExtractionTargets(hopperMinecartData, config.extractionTargetBoxScale);
            }

            if (!hasTarget && config.renderHopperRanges) {
                AABB[] rangeBoxes = InfoRenderer.buildHopperRangeBoxes(entity, anchor);
                float rangeScale = config.hopperRangeBoxScale;
                float[] pickupColor = Colors.rgbFloats(config.pickupRangeColor,
                        MinecartVisualizerConfig.DEFAULT_PICKUP_RANGE_COLOR.getRGB());
                float[] extractionColor = Colors.rgbFloats(config.extractionRangeColor,
                        MinecartVisualizerConfig.DEFAULT_EXTRACTION_RANGE_COLOR.getRGB());

                if (config.hopperVisualOnTop) {
                    InfoRenderer.queueWorldBox(rangeBoxes[0], rangeScale, pickupColor);
                    InfoRenderer.queueWorldBox(rangeBoxes[1], rangeScale, extractionColor);
                } else {
                    InfoRenderer.renderHopperRanges(entity, anchor, cameraX, cameraY, cameraZ, vertexConsumers,
                            pickupColor, extractionColor, rangeScale);
                }
            }
        }
    }

    @Unique
    private void renderTextInfo(T entity, MinecartsGroup group,
                                PoseStack matrices, MultiBufferSource.BufferSource vertexConsumers) {
        var config = MinecartVisualizerConfig.getInstance();
        if (!config.enableMinecartVisualization) return;
        if (!config.enableInfoTextDisplay) return;

        Player player = Minecraft.getInstance().player;
        if (player != null && entity.distanceToSqr(player) > config.infoRenderDistance * config.infoRenderDistance) return;
        if (config.mergeStackingMinecartInfo && group != null && !entity.getUUID().equals(group.getLeader())) return;

        MinecartDataPayload displayInfo = MinecartClientHandler.getFreshMinecartData(entity.getUUID());
        if (displayInfo == null) return;
        TNTMinecartDataPayload tntMinecartDisplayInfo = null;
        if (config.trackTNTMinecart && entity instanceof MinecartTNT) {
            tntMinecartDisplayInfo = MinecartClientHandler.getTNTMinecartData(entity.getUUID());
        }
        List<MutableComponent> infoTexts = new ArrayList<>(InfoRenderer.getInfoTexts(displayInfo));

        if (tntMinecartDisplayInfo != null) {
            infoTexts.addAll(InfoRenderer.getTNTMinecartInfoTexts(tntMinecartDisplayInfo));
        }

        if (config.enableDirectionDisplay) {
            String direction = MinecartVisualizerUtils.getMovementDirection(displayInfo.velocity());
            infoTexts.add(Component.translatable("info.minecartvisualizer.direction", direction));
        }

        if (config.mergeStackingMinecartInfo && config.enableStackedCountDisplay) {
            int stackingMinecarts = MinecartClientHandler.getGroupSize(entity.getUUID());
            if (stackingMinecarts > 1)
                infoTexts.add(Component.literal("x" + stackingMinecarts).withStyle(ChatFormatting.YELLOW));
        }

        if (config.enableSignalStrengthDisplay && (entity instanceof AbstractMinecartContainer)) {
            UUID targetUuid;
            if (config.mergeStackingMinecartInfo && group != null) {
                UUID priority = MinecartClientHandler.getPriority(group);
                targetUuid = priority != null ? priority : entity.getUUID();
            } else {
                targetUuid = entity.getUUID();
            }

            HopperMinecartDataPayload hopperData = MinecartClientHandler.getFreshHopperMinecartData(targetUuid);
            if (hopperData != null) {
                int signal = calculateRedstoneSignal(hopperData.items());
                infoTexts.add(Component.translatable("info.minecartvisualizer.signal", signal).withStyle(ChatFormatting.RED));
            }
        }

        if (config.enableShortIdDisplay && entity instanceof MinecartHopper) {
            if (TrackersManager.hasBeenTracked(entity.getUUID())) {
                HopperMinecartTracker tracker = TrackersManager.getTracker(entity.getUUID());
                if (config.enableTrackerRuntimeDisplay) {
                    long runtime = tracker.getRunTime();
                    MinecartVisualizerConfig.TimeUnit unit = config.trackerTimeUnit;

                    if (unit == MinecartVisualizerConfig.TimeUnit.TICK) {
                        infoTexts.add(Component.translatable("info.minecartvisualizer.runtime_tick", runtime));
                    } else {
                        double convertedTime = (double) runtime / unit.getTicksPerUnit();
                        infoTexts.add(Component.translatable("info.minecartvisualizer.runtime",
                                String.format("%." + config.accuracy + "f", convertedTime),
                                unit.getLabel()));
                    }
                }
                String shortUuid = tracker.getShortUuid();
                TrackerColor trackerColor = tracker.getTrackerColor();
                infoTexts.add(Component.literal("ID: " + shortUuid).withColor(trackerColor.getHex()));
            }
        }


        double textYOffset = getTextYOffset(entity, group, config);

        matrices.pushPose();
        matrices.translate(0.0, textYOffset, 0.0);
        int infoTextColor = Colors.rgb(config.infoTextColor, MinecartVisualizerConfig.DEFAULT_INFO_TEXT_COLOR.getRGB()) | 0xFF000000;
        if (config.infoTextOnTop) {
            //置顶渲染：不在这里画，交给置顶阶段（清空深度之后）统一绘制
            InfoRenderer.queueInfoTexts(infoTexts, entity, matrices, infoTextColor);
        } else {
            InfoRenderer.renderTexts(infoTexts, entity, matrices, vertexConsumers, infoTextColor);
        }
        matrices.popPose();
    }

    @Unique
    private static <T extends Entity> double getTextYOffset(T entity, MinecartsGroup group, MinecartVisualizerConfig config) {
        double textYOffset = 0;
        if (entity instanceof MinecartHopper) {
            int totalItemsToRender = getTotalItemsToRender(group, config);

            if ((config.maxInventorySlotsToRender == 0) || (totalItemsToRender <= config.maxInventorySlotsToRender)) {
                if (totalItemsToRender > 0) {
                    int finalCols;
                    if (config.autoSizeColumns) {
                        finalCols = getAdaptiveColumns(totalItemsToRender);
                    } else {
                        finalCols = config.inventoryCols;
                    }
                    int rows = (totalItemsToRender + finalCols - 1) / finalCols;
                    textYOffset = rows * 0.29 + 0.1;
                }
            }
        }
        return textYOffset;
    }

    @Unique
    private static int getTotalItemsToRender(MinecartsGroup group, MinecartVisualizerConfig config) {
        int slotsPerMinecart = 0;
        for (boolean enabled : config.hopperSlotFilter) {
            if (enabled) slotsPerMinecart++;
        }

        int totalItemsToRender;

        if (config.mergeStackingMinecartInfo && group != null) {
            totalItemsToRender = config.foldInventory ? slotsPerMinecart : group.getMinecarts().size() * slotsPerMinecart;
        } else {
            totalItemsToRender = slotsPerMinecart;
        }
        return totalItemsToRender;
    }

    @Unique
    public int calculateRedstoneSignal (List < ItemStack > inventory) {
        if (inventory == null || inventory.isEmpty()) return 0;

        float totalFullness = 0;
        boolean hasAnyItem = false;

        for (ItemStack stack : inventory) {
            if (!stack.isEmpty()) {
                totalFullness += (float) stack.getCount() / stack.getMaxStackSize();
                hasAnyItem = true;
            }
        }
        if (!hasAnyItem) {
            return 0;
        }
        int signal = (int) Math.floor(1 + (totalFullness / inventory.size()) * 14);
        return Math.min(15, signal);
    }
}
