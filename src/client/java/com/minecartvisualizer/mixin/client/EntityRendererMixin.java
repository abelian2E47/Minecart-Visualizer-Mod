package com.minecartvisualizer.mixin.client;
import com.minecartvisualizer.*;
import com.minecartvisualizer.config.MinecartVisualizerConfig;
import com.minecartvisualizer.tracker.HopperMinecartTracker;
import com.minecartvisualizer.tracker.TrackerColor;
import com.minecartvisualizer.tracker.TrackersManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.entity.vehicle.HopperMinecartEntity;
import net.minecraft.entity.vehicle.StorageMinecartEntity;
import net.minecraft.entity.vehicle.TntMinecartEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.ArrayList;
import java.util.WeakHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static com.minecartvisualizer.InfoRenderer.getAdaptiveColumns;

@Mixin(EntityRenderer.class)
public class EntityRendererMixin<T extends Entity, S extends EntityRenderState> {
    @Unique
    private final Map<S, T> stateToEntity = new WeakHashMap<>();

    @Inject(
            method = "updateRenderState",
            at = @At("TAIL")
    )
    private void captureEntity(T entity, S state, float tickProgress, CallbackInfo ci) {
        stateToEntity.put(state, entity);
    }

    @Inject(
            method = "render",
            at = @At("HEAD")
    )
    private void renderMinecartInfo(
            S renderState, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState cameraState, CallbackInfo ci
    ) {
        T entity = stateToEntity.get(renderState);
        if (entity == null) return;

        if (!(entity instanceof AbstractMinecartEntity)) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        double cameraX = client.gameRenderer.getCamera().getCameraPos().x;
        double cameraY = client.gameRenderer.getCamera().getCameraPos().y;
        double cameraZ = client.gameRenderer.getCamera().getCameraPos().z;
        client.getRenderTickCounter().getTickProgress(true);
        VertexConsumerProvider.Immediate vertexConsumers = client.getBufferBuilders().getEntityVertexConsumers();

        if (entity instanceof HopperMinecartEntity) {
            HopperMinecartDataPayload hopperMinecartData = MinecartClientHandler.getHopperMinecartData(entity.getUuid());
            if (!InfoRenderer.shouldRender(entity)) {
                if (MinecartVisualizerClient.uuid != null && entity.getUuid().equals(MinecartVisualizerClient.uuid)) {
                    hopperMinecartData = MinecartClientHandler.getHopperMinecartData(MinecartVisualizerClient.uuid);
                }
            }
            if (hopperMinecartData != null) {
                MinecartsGroup group = MinecartClientHandler.getGroup(entity.getUuid());
                boolean isLocked = !hopperMinecartData.enable();
                renderHopperMinecartInfo(hopperMinecartData, entity, group, isLocked, renderState, cameraX, cameraY, cameraZ, matrices, vertexConsumers, queue);
            }
        }

        MinecartsGroup group = MinecartClientHandler.getGroup(entity.getUuid());
        renderTextInfo(entity, group, matrices, vertexConsumers);
    }

    @Unique
    private void renderHopperMinecartInfo(HopperMinecartDataPayload hopperMinecartData,T entity, MinecartsGroup group,
                                          boolean isLocked, S renderState,
                                          double cameraX, double cameraY, double cameraZ,
                                          MatrixStack matrices, VertexConsumerProvider.Immediate vertexConsumers,
                                          OrderedRenderCommandQueue queue) {
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
                    HopperMinecartDataPayload data = MinecartClientHandler.getHopperMinecartData(leaderUuid);
                    if (data != null) {
                        List<ItemStack> filteredItems = InfoRenderer.filterItems(data.items());
                        int finalCols = getFinalCols.apply(totalSlots);
                        InfoRenderer.renderInventory(
                                filteredItems, entity.getEntityWorld(),
                                renderState.x, renderState.y, renderState.z, totalSlots, finalCols,
                                cameraX, cameraY, cameraZ,
                                matrices, vertexConsumers, queue, isLocked
                        );
                    }
                } else {
                    List<ItemStack> allItems = new ArrayList<>();
                    for (UUID minecartUuid : minecarts) {
                        HopperMinecartDataPayload data = MinecartClientHandler.getHopperMinecartData(minecartUuid);
                        if (data != null) {
                            allItems.addAll(data.items());
                        }
                    }
                    if (config.maxInventorySlotsToRender == 0 || totalSlots <= config.maxInventorySlotsToRender) {
                        List<ItemStack> filteredItems = InfoRenderer.filterItems(allItems);
                        int finalCols = getFinalCols.apply(totalSlots);
                        InfoRenderer.renderInventory(
                                filteredItems, entity.getEntityWorld(),
                                renderState.x, renderState.y, renderState.z, totalSlots, finalCols,
                                cameraX, cameraY, cameraZ,
                                matrices, vertexConsumers, queue, isLocked
                        );
                    }
                }
            } else {
                int totalSlots = slotsPerMinecart;
                List<ItemStack> filteredItems = InfoRenderer.filterItems(hopperMinecartData.items());
                int finalCols = getFinalCols.apply(totalSlots);
                InfoRenderer.renderInventory(
                        filteredItems, entity.getEntityWorld(),
                        renderState.x, renderState.y, renderState.z, totalSlots, finalCols,
                        cameraX, cameraY, cameraZ,
                        matrices, vertexConsumers, queue, isLocked
                );
            }
        }

        if (!isLocked && config.highlightExtractionTargets) {
            boolean hasTargets = InfoRenderer.highlightExtractionTargets(entity, cameraX, cameraY, cameraZ, matrices, vertexConsumers);
            if (!hasTargets && config.renderHopperRanges) {
                InfoRenderer.renderHopperRanges(entity, cameraX, cameraY, cameraZ, matrices, vertexConsumers);
            }
        }
    }

    @Unique
    private void renderTextInfo(T entity, MinecartsGroup group,
                                MatrixStack matrices, VertexConsumerProvider.Immediate vertexConsumers) {
        var config = MinecartVisualizerConfig.getInstance();
        if (!config.enableMinecartVisualization) return;
        if (!config.enableInfoTextDisplay) return;

        PlayerEntity player = MinecraftClient.getInstance().player;
        if (player != null && entity.squaredDistanceTo(player) > config.infoRenderDistance * config.infoRenderDistance) return;
        if (config.mergeStackingMinecartInfo && group != null && !entity.getUuid().equals(group.getLeader())) return;

        MinecartDataPayload displayInfo = MinecartClientHandler.getMinecartData(entity.getUuid());
        if (displayInfo == null) return;
        TNTMinecartDataPayload tntMinecartDisplayInfo = null;
        if (config.trackTNTMinecart && entity instanceof TntMinecartEntity) {
            tntMinecartDisplayInfo = MinecartClientHandler.getTNTMinecartData(entity.getUuid());
        }

        List<MutableText> infoTexts = new ArrayList<>(InfoRenderer.getInfoTexts(displayInfo));

        if (tntMinecartDisplayInfo != null) {
            infoTexts.addAll(InfoRenderer.getTNTMinecartInfoTexts(tntMinecartDisplayInfo));
        }

        if (config.enableDirectionDisplay) {
            String direction = MinecartVisualizerUtils.getMovementDirection(displayInfo.velocity());
            infoTexts.add(Text.translatable("info.minecartvisualizer.direction", direction));
        }

        if (config.mergeStackingMinecartInfo && config.enableStackedCountDisplay) {
            int stackingMinecarts = MinecartClientHandler.getGroupSize(entity.getUuid());
            if (stackingMinecarts > 1)
                infoTexts.add(Text.literal("x" + stackingMinecarts).formatted(Formatting.YELLOW));
        }

        if (config.enableSignalStrengthDisplay && (entity instanceof StorageMinecartEntity)) {
            UUID targetUuid;
            if (config.mergeStackingMinecartInfo && group != null) {
                UUID priority = MinecartClientHandler.getPriority(group);
                targetUuid = priority != null ? priority : entity.getUuid();
            } else {
                targetUuid = entity.getUuid();
            }

            HopperMinecartDataPayload hopperData = MinecartClientHandler.getHopperMinecartData(targetUuid);
            if (hopperData != null) {
                int signal = calculateRedstoneSignal(hopperData.items());
                infoTexts.add(Text.translatable("info.minecartvisualizer.signal", signal).formatted(Formatting.RED));
            }
        }

        if (config.enableShortIdDisplay && entity instanceof HopperMinecartEntity) {
            if (TrackersManager.hasBeenTracked(entity.getUuid())) {
                HopperMinecartTracker tracker = TrackersManager.getTracker(entity.getUuid());
                if (config.enableTrackerRuntimeDisplay) {
                    long runtime = tracker.getRunTime();
                    MinecartVisualizerConfig.TimeUnit unit = config.trackerTimeUnit;

                    if (unit == MinecartVisualizerConfig.TimeUnit.TICK) {
                        infoTexts.add(Text.translatable("info.minecartvisualizer.runtime_tick", runtime));
                    } else {
                        double convertedTime = (double) runtime / unit.getTicksPerUnit();
                        infoTexts.add(Text.translatable("info.minecartvisualizer.runtime",
                                String.format("%." + config.accuracy + "f", convertedTime),
                                unit.getLabel()));
                    }
                }
                String shortUuid = tracker.getShortUuid();
                TrackerColor trackerColor = tracker.getTrackerColor();
                infoTexts.add(Text.literal("ID: " + shortUuid).withColor(trackerColor.getHex()));
            }
        }

        double textYOffset = getTextYOffset(entity, group, config);

        matrices.push();
        matrices.translate(0, textYOffset, 0);
        InfoRenderer.renderTexts(infoTexts, entity, matrices, vertexConsumers);
        matrices.pop();
    }

    @Unique
    private static <T extends Entity> double getTextYOffset(T entity, MinecartsGroup group, MinecartVisualizerConfig config) {
        double textYOffset = 0;
        if (entity instanceof HopperMinecartEntity) {
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
                totalFullness += (float) stack.getCount() / stack.getMaxCount();
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
