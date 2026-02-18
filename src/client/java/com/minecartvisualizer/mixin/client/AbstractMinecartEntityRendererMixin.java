package com.minecartvisualizer.mixin.client;

import com.minecartvisualizer.*;
import com.minecartvisualizer.config.MinecartVisualizerConfig;
import com.minecartvisualizer.tracker.HopperMinecartTracker;
import com.minecartvisualizer.tracker.TrackerColor;
import com.minecartvisualizer.tracker.TrackersManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.AbstractMinecartEntityRenderer;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.state.MinecartEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.entity.vehicle.HopperMinecartEntity;
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
import java.util.List;
import java.util.UUID;

import static com.minecartvisualizer.InfoRenderer.getAdaptiveColumns;


@Mixin(AbstractMinecartEntityRenderer.class)
public abstract class AbstractMinecartEntityRendererMixin extends EntityRenderer {

    @Unique
    private AbstractMinecartEntity entity;


    protected AbstractMinecartEntityRendererMixin(EntityRendererFactory.Context context) {
        super(context);
    }

    @Inject(
            method = "updateRenderState*",
            at = @At("TAIL")
    )
    private void getEntity(AbstractMinecartEntity entity,
                           MinecartEntityRenderState state,
                           float tickDelta,
                           CallbackInfo ci) {
        this.entity = entity;
    }


    @Inject(
            method = "render(Lnet/minecraft/client/render/entity/state/MinecartEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("TAIL")
    )
    public void renderInfo(MinecartEntityRenderState state,
                           MatrixStack matrices,
                           VertexConsumerProvider vertexConsumer,
                           int light,
                           CallbackInfo ci) {
        var config = MinecartVisualizerConfig.getInstance();

        MinecartDataPayload displayInfo = MinecartClientHandler.getMinecartData(entity.getUuid());
        TNTMinecartDataPayload tntMinecartDisplayInfo = null;

        if (config.trackTNTMinecart && entity instanceof TntMinecartEntity) {
            tntMinecartDisplayInfo = MinecartClientHandler.getTNTMinecartData(entity.getUuid());
        }

        if (!InfoRenderer.shouldRender(entity)) {
            if (MinecartVisualizerClient.uuid != null && entity.getUuid().equals(MinecartVisualizerClient.uuid)) {
                displayInfo = MinecartClientHandler.getMinecartData(MinecartVisualizerClient.uuid);
            } else {
                return;
            }
        }

        if (displayInfo == null) {
            return;
        }

        List<MutableText> infoTexts = new ArrayList<>();

        //文本信息渲染
        if (config.enableInfoTextDisplay) {
            infoTexts.addAll(InfoRenderer.getInfoTexts(displayInfo));

            if (tntMinecartDisplayInfo != null) {
                infoTexts.addAll(InfoRenderer.getTNTMinecartInfoTexts(tntMinecartDisplayInfo));
            }

            //方向
            if (config.enableDirectionDisplay){
                String direction = MinecartVisualizerUtils.getMovementDirection(displayInfo.velocity());
                infoTexts.add(Text.translatable("info.minecartvisualizer.direction", direction));
            }

            if (config.mergeStackingMinecartInfo && config.enableStackedCountDisplay){
                int stackingMinecarts = MinecartClientHandler.getGroupSize(entity.getUuid());
                if (stackingMinecarts > 1) infoTexts.add(Text.literal("x" + stackingMinecarts).formatted(Formatting.YELLOW));
            }

            //信号强度
            if (config.enableSignalStrengthDisplay){
                UUID targetUuid = config.mergeStackingMinecartInfo
                        ? MinecartClientHandler.getPriority(MinecartClientHandler.getGroup(entity.getUuid()))
                        : entity.getUuid();

                if (entity instanceof HopperMinecartEntity){
                    List<ItemStack> inventory = MinecartClientHandler.getHopperMinecartData(targetUuid).items();
                    int signal = calculateRedstoneSignal(inventory);
                    infoTexts.add(Text.translatable("info.minecartvisualizer.signal", signal).formatted(Formatting.RED));
                }
            }

            //矿车ID
            if (config.enableShortIdDisplay && entity instanceof HopperMinecartEntity){
                if(TrackersManager.hasBeenTracked(entity.getUuid())){
                    HopperMinecartTracker tracker = TrackersManager.getTracker(entity.getUuid());
                    //运行时间
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

            //计算文本偏移量
            double textYOffset;
            //计算单个矿车被允许渲染的槽位数
            int slotsPerMinecart = 0;
            for (boolean enabled : config.hopperSlotFilter) {
                if (enabled) slotsPerMinecart++;
            }

            //确定总渲染格数
            int totalItemsToRender = 0;

            //计算实际要渲染的物品栏总数
            if (config.mergeStackingMinecartInfo) {
                MinecartsGroup group = MinecartClientHandler.getGroup(entity.getUuid());
                if (group != null) {
                    totalItemsToRender = config.foldInventory ? slotsPerMinecart : group.getMinecarts().size() * slotsPerMinecart;
                }
            } else {
                totalItemsToRender = slotsPerMinecart;
            }

            if ((config.maxInventorySlotsToRender == 0) || (totalItemsToRender <= config.maxInventorySlotsToRender)) {
                if (totalItemsToRender > 0) {
                    int finalCols;
                    if (config.autoSizeColumns) {
                        finalCols = getAdaptiveColumns(totalItemsToRender);
                    } else {
                        finalCols = config.inventoryCols;
                    }
                    //计算实际占用行数
                    int rows = (totalItemsToRender + finalCols - 1) / finalCols;
                    //计算Y轴偏移
                    textYOffset = rows * 0.5;
                } else {
                    textYOffset = 0;
                }
            } else {
                textYOffset = 0;
            }

            matrices.push();
            matrices.translate(0, textYOffset, 0);
            InfoRenderer.renderTexts(infoTexts, entity, matrices, vertexConsumer);

            matrices.pop();

        }
    }

    @Unique
    public int calculateRedstoneSignal(List<ItemStack> inventory) {
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