package com.minecartvisualizer;

import com.minecartvisualizer.config.MinecartVisualizerConfig;
import com.minecartvisualizer.tracker.HopperMinecartTracker;
import com.minecartvisualizer.tracker.TrackerColor;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.*;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ModelTransformationMode;
import net.minecraft.text.MutableText;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;


public class InfoRenderer {
    public static boolean getCustomRenderLayer;

    public static void renderTexts(List<MutableText> infoTexts, Entity entity, MatrixStack matrices, VertexConsumerProvider vertexConsumer) {
        var config = MinecartVisualizerConfig.getInstance();
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;

        float baseHeight = entity.getHeight() + 0.5f;

        Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();
        float cameraYaw = camera.getYaw();
        float cameraPitch = camera.getPitch();

        matrices.push();

        matrices.translate(0.0, baseHeight, 0.0);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-cameraYaw + 180));

        if (config.alwaysFacingThePlayer) {
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-cameraPitch));
        }

        matrices.scale(0.03f, -0.03f, 0.03f);
        Matrix4f matrix4f = matrices.peek().getPositionMatrix();

        float startY = -(infoTexts.size() * 10);

        renderTextLayer(infoTexts, textRenderer, matrix4f, vertexConsumer, startY, true);
        renderTextLayer(infoTexts, textRenderer, matrix4f, vertexConsumer, startY, false);

        matrices.pop();
    }

    private static void renderTextLayer(List<MutableText> texts, TextRenderer renderer, Matrix4f matrix, VertexConsumerProvider vc, float y, boolean isBackground) {
        float currentY = y;
        for (MutableText text : texts) {
            float x = -renderer.getWidth(text) / 2f;
            if (isBackground) {
                renderer.draw(text, x, currentY, -2130706433, false, matrix, vc, TextRenderer.TextLayerType.SEE_THROUGH, 0x4CC8C8C8, 0xF000F0);
            } else {
                renderer.draw(text, x, currentY, 0xFFFFFFFF, false, matrix, vc, TextRenderer.TextLayerType.NORMAL, 0, 0xF000F0);
            }
            currentY += 10;
        }
    }

    public static void renderInventory(List<ItemStack> items, Entity entity,
                                       int cols,
                                       double cameraX, double cameraY, double cameraZ,
                                       float tickDelta, MatrixStack matrices,
                                       boolean isLocked) {
        var config = MinecartVisualizerConfig.getInstance();
        if (items == null || items.isEmpty()) return;

        VertexConsumerProvider.Immediate immediate = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();

        //位置插值
        double x = MathHelper.lerp(tickDelta, entity.lastRenderX, entity.getX());
        double y = MathHelper.lerp(tickDelta, entity.lastRenderY, entity.getY());
        double z = MathHelper.lerp(tickDelta, entity.lastRenderZ, entity.getZ());
        double itemY = y + 1.4;

        Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();

        matrices.push();
        matrices.translate(x - cameraX, itemY - cameraY, z - cameraZ);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw() + 180));

        if (config.alwaysFacingThePlayer) {
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-camera.getPitch()));
        }

        for (int i = 0; i < items.size(); i++) {
            int row = i / cols;
            int col = i % cols;

            renderSingleItemSlot(
                    items.get(i),
                    row,
                    col,
                    cols,
                    matrices,
                    immediate,
                    isLocked,
                    config.enableItemStackCountDisplay
            );
        }

        matrices.pop();
    }

    private static void renderSingleItemSlot(ItemStack item, int row, int col, int cols,
                                             MatrixStack matrices, VertexConsumerProvider immediate,
                                             boolean isLocked, boolean enableCount) {
        ItemRenderer itemRenderer = MinecraftClient.getInstance().getItemRenderer();
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;

        matrices.push();

        double xOffset = (col - (cols - 1) / 2.0) * 0.5;
        double yOffset = row * 0.5;

        matrices.translate(xOffset, yOffset, 0.0);

        VertexConsumer buffer = immediate.getBuffer(RenderLayer.getGui());
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        boolean changeColor = isLocked && MinecartVisualizerConfig.getInstance().enableHopperMinecartEnableDisplay;

        float r1 = changeColor ? 0.8f : 0.53f;
        float g1 = changeColor ? 0.4f : 0.53f;
        float b1 = changeColor ? 0.4f : 0.53f;

        float r2 = changeColor ? 1.0f : 0.863f;
        float g2 = changeColor ? 0.0f : 0.863f;
        float b2 = changeColor ? 0.0f : 0.863f;

        drawRect(matrix, buffer, 0.19f, -0.06f, r1, g1, b1, 0.5f);//背景
        drawRect(matrix, buffer, 0.22f, -0.08f, r2, g2, b2, 0.7f);//边框

        matrices.push();
        matrices.scale(0.38f, 0.38f, 0.38f);
        getCustomRenderLayer = true;
        itemRenderer.renderItem(item, ModelTransformationMode.GUI, 0xF000F0,
                OverlayTexture.DEFAULT_UV, matrices, immediate, null, 0);
        getCustomRenderLayer = false;
        matrices.pop();

        if (enableCount && item.getCount() > 1) {
            String countString = String.valueOf(item.getCount());
            matrices.push();
            matrices.translate(0.12, -0.1, 0.1);
            matrices.scale(0.017f, -0.017f, 0.017f);
            textRenderer.draw(countString, 0.0f, 0.0f, 0xFFFFFFFF, false,
                    matrices.peek().getPositionMatrix(), immediate,
                    TextRenderer.TextLayerType.SEE_THROUGH, 0, 0xF000F0);
            matrices.pop();
        }

        matrices.pop();
    }

    public static void renderHopperRanges(Entity entity, double cameraX, double cameraY, double cameraZ,
                                          MatrixStack matrices, VertexConsumerProvider vertexConsumers) {
        VertexConsumer lines = vertexConsumers.getBuffer(RenderLayer.getLines());

        Box pickupBox = entity.getBoundingBox().expand(0.25, 0.0, 0.25);
        Box viewPickupBox = pickupBox.offset(-cameraX, -cameraY, -cameraZ);
        VertexRendering.drawBox(matrices, lines, viewPickupBox, 1.0f, 1.0f, 0.1f, 0.8f);

        double minX = entity.getX() - 0.5;
        double minZ = entity.getZ() - 0.5;
        double minY = entity.getY() + 0.7;
        double maxX = entity.getX() + 0.5;
        double maxZ = entity.getZ() + 0.5;
        double maxY = entity.getY() + 2;

        Box extractionBox = new Box(minX, minY, minZ, maxX, maxY, maxZ);
        Box viewExtractionBox = extractionBox.offset(-cameraX, -cameraY, -cameraZ);
        VertexRendering.drawBox(matrices, lines, viewExtractionBox, 1.0f, 1.0f, 0.1f, 0.8f);
    }

    public static boolean highlightExtractionTargets(Entity entity, double cameraX, double cameraY, double cameraZ,
                                                  MatrixStack matrices, VertexConsumerProvider vertexConsumers) {
        World world = entity.getWorld();
        VertexConsumer lines = vertexConsumers.getBuffer(RenderLayer.getLines());
        boolean hasTarget = false;

        Box extractionArea = new Box(
                entity.getX() - 0.5, entity.getY() + 1, entity.getZ() - 0.5,
                entity.getX() + 0.5, entity.getY() + 2, entity.getZ() + 0.5
        );

        //方块实体
        BlockPos targetPos = BlockPos.ofFloored(entity.getX(), entity.getY() + 1.5, entity.getZ());
        BlockState state = world.getBlockState(targetPos);

        if (world.getBlockEntity(targetPos) != null) {
            VoxelShape shape = state.getOutlineShape(world, targetPos);
            if (!shape.isEmpty()) {
                hasTarget = true;
                for (Box box : shape.getBoundingBoxes()) {
                    Box viewBox = box.offset(targetPos.getX() - cameraX,
                            targetPos.getY() - cameraY,
                            targetPos.getZ() - cameraZ);
                    VertexRendering.drawBox(matrices, lines, viewBox.expand(0.005), 0.0f, 1.0f, 0.0f, 1.0f);
                }
            }
        }

        //实体
        List<Entity> inventories = world.getOtherEntities(entity, extractionArea, e ->
                e instanceof net.minecraft.entity.vehicle.VehicleInventory && e.isAlive()
        );

        if (!inventories.isEmpty()) {
            hasTarget = true;
            for (Entity invEntity : inventories) {
                Box viewBox = invEntity.getBoundingBox().offset(-cameraX, -cameraY, -cameraZ);
                VertexRendering.drawBox(matrices, lines, viewBox.expand(0.01), 0.0f, 1.0f, 0.0f, 1.0f);
            }
        }

        return hasTarget;
    }

    public static void renderTrail(HopperMinecartTracker tracker,
                                   MatrixStack matrices, VertexConsumer lineConsumer) {
        Collection<Vec3d> points = tracker.getTrailPoints();
        if (points.size() < 2) return;

        Matrix4f matrix4f = matrices.peek().getPositionMatrix();

        int hex = tracker.getTrackerColor().getHex();
        float r = ((hex >> 16) & 0xFF) / 255f;
        float g = ((hex >> 8) & 0xFF) / 255f;
        float b = (hex & 0xFF) / 255f;

        Vec3d yOffset = new Vec3d(0, 0.5, 0);

        RenderSystem.lineWidth(3.0f);

        Iterator<Vec3d> it = points.iterator();
        if (!it.hasNext()) return;

        Vec3d prevPoint = it.next().add(yOffset);

        while (it.hasNext()) {
            Vec3d currentPoint = it.next().add(yOffset);
            drawLine(prevPoint, currentPoint, matrix4f, lineConsumer, r, g, b);
            prevPoint = currentPoint;
        }

        RenderSystem.lineWidth(1.0f);
    }

    private static void drawRect(Matrix4f matrix, VertexConsumer buffer, float s, float z, float r, float g, float b, float a) {
        buffer.vertex(matrix, -s, -s, z).color(r, g, b, a);
        buffer.vertex(matrix, s, -s, z).color(r, g, b, a);
        buffer.vertex(matrix, s, s, z).color(r, g, b, a);
        buffer.vertex(matrix, -s, s, z).color(r, g, b, a);
    }

    public static void drawLine(Vec3d startPoint, Vec3d endPoint,
                                Matrix4f matrix, VertexConsumer lineConsumer,
                                float r, float g, float b) {

        float startX = (float)(startPoint.x);
        float startY = (float)(startPoint.y);
        float startZ = (float)(startPoint.z);

        float endX = (float)(endPoint.x);
        float endY = (float)(endPoint.y);
        float endZ = (float)(endPoint.z);

        Vector3f normal = endPoint.subtract(startPoint).toVector3f();

        lineConsumer.vertex(matrix, startX, startY, startZ).color(r, g, b, 1.0f).normal(normal.x,normal.y,normal.z);
        lineConsumer.vertex(matrix, endX, endY, endZ).color(r, g, b, 1.0f).normal(normal.x,normal.y,normal.z);
    }

    public static boolean shouldRender(Entity entity) {
        var config = MinecartVisualizerConfig.getInstance();

        if (!config.enableMinecartVisualization) {
            return false;
        }

        PlayerEntity player = MinecraftClient.getInstance().player;
        if (player != null && entity.squaredDistanceTo(player) > config.infoRenderDistance * config.infoRenderDistance) {
            return false;
        }

        return !config.mergeStackingMinecartInfo || MinecartClientHandler.isLeader(entity.getUuid());
    }

    public static List<MutableText> getInfoTexts(MinecartDataPayload displayInfo){
        var config = MinecartVisualizerConfig.getInstance();
        MinecartVisualizerConfig.SpeedUnit unit = config.speedUnit;
        boolean isMps = (unit == MinecartVisualizerConfig.SpeedUnit.METERS_PER_SECOND);
        boolean[] enableSettings = {
                config.enablePosTextDisplay,
                config.enableVelocityTextDisplay,
                config.enableYawTextDisplay,
                config.enableSpeedTextDisplay,
                isMps
        };

        return displayInfo.getInfoTexts(config.accuracy, enableSettings);
    }

    public static List<MutableText> getTNTMinecartInfoTexts(TNTMinecartDataPayload displayInfo){
        var config = MinecartVisualizerConfig.getInstance();

        boolean[] enableSettings = {
                config.enableTNTWobbleDisplay,
                config.enableTNTFuseTicksDisplay,
        };

        return displayInfo.getInfoTexts(enableSettings);
    }

    public static List<ItemStack> filterItems(List<ItemStack> originalItems) {
        var config = MinecartVisualizerConfig.getInstance();
        List<ItemStack> filtered = new ArrayList<>();

        for (int i = 0; i < originalItems.size(); i++) {
            if (config.hopperSlotFilter[i % 5]) {
                filtered.add(originalItems.get(i));
            }
        }
        return filtered;
    }

    public static int getAdaptiveColumns(int totalSlots) {
        if (totalSlots <= 5){
            return totalSlots;
        }else if (totalSlots < 27) {
            return 5;
        } else if (totalSlots <= 54){
            return 9;
        } else {
            return (int) Math.sqrt(totalSlots);
        }
    }

    public static void drawTrackerPointBox(MatrixStack matrices, VertexConsumer lines, TrackerColor color,
                                           BlockPos targetPos, boolean active) {
        int hex = color.getHex();
        float r = ((hex >> 16) & 0xFF) / 255.0f;
        float g = ((hex >> 8) & 0xFF) / 255.0f;
        float b = (hex & 0xFF) / 255.0f;

        float a;
        if (active) {
            a = 1.0f;
        } else {
            a = 0.6f;
            r *= 0.7f; g *= 0.7f; b *= 0.7f;
        }

        double minX = targetPos.getX();
        double minY = targetPos.getY() ;
        double minZ = targetPos.getZ();

        Box standardBox = new Box(minX, minY, minZ, minX + 1.0, minY + 1.0, minZ + 1.0);

        VertexRendering.drawBox(matrices, lines, standardBox.expand(0.005), r, g, b, a);
    }
}



