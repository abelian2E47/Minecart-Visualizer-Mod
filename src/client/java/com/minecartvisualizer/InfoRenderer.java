package com.minecartvisualizer;

import com.minecartvisualizer.config.MinecartVisualizerConfig;
import com.minecartvisualizer.tracker.HopperMinecartTracker;
import com.minecartvisualizer.tracker.TrackerColor;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.item.ItemModelManager;
import net.minecraft.client.render.*;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.HopperMinecartEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.World;
import net.minecraft.client.render.RenderLayers;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Unique;

import java.util.*;


public class InfoRenderer {

    public static void renderTexts(List<MutableText> infoTexts, Entity entity, MatrixStack matrices, VertexConsumerProvider vertexConsumer) {
        var config = MinecartVisualizerConfig.getInstance();
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
        float baseHeight = entity.getHeight() + 0.3f;
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

    private static final List<QueuedInventory> queuedInventories = new ArrayList<>();

    public static void queueInventory(List<ItemStack> items, World world,
                                      double lerpedX, double lerpedY, double lerpedZ,
                                      int totalSlots, int cols, boolean isLocked) {
        if (totalSlots > 0) {
            queuedInventories.add(new QueuedInventory(items, world, lerpedX, lerpedY, lerpedZ, totalSlots, cols, isLocked));
        }
    }

    public static void beginInventoryRenderFrame() {
        queuedInventories.clear();
    }

    public static boolean hasQueuedInventories() {
        return !queuedInventories.isEmpty();
    }

    public static void renderQueuedInventories() {
        if (queuedInventories.isEmpty()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        Vec3d cameraPos = client.gameRenderer.getCamera().getCameraPos();
        MatrixStack matrices = new MatrixStack();
        VertexConsumerProvider.Immediate vertexConsumers = client.getBufferBuilders().getEntityVertexConsumers();
        OrderedRenderCommandQueue itemRenderQueue = client.gameRenderer.getEntityRenderCommandQueue();

        try {
            for (QueuedInventory inventory : queuedInventories) {
                renderInventory(inventory, cameraPos, matrices, vertexConsumers, itemRenderQueue);
            }

            client.gameRenderer.getEntityRenderDispatcher().render();
            vertexConsumers.draw();
        } finally {
            queuedInventories.clear();
        }
    }

    private static void renderInventory(QueuedInventory inventory, Vec3d cameraPos,
                                        MatrixStack matrices, VertexConsumerProvider.Immediate vertexConsumers,
                                        OrderedRenderCommandQueue itemRenderQueue) {
        var config = MinecartVisualizerConfig.getInstance();
        Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();
        matrices.push();
        matrices.translate(inventory.lerpedX() - cameraPos.x, inventory.lerpedY() + 0.4 - cameraPos.y, inventory.lerpedZ() - cameraPos.z);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw() + 180));
        if (config.alwaysFacingThePlayer) {
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-camera.getPitch()));
        }

        for (int i = 0; i < inventory.totalSlots(); i++) {
            int row = i / inventory.cols();
            int col = i % inventory.cols();
            renderSlotBackground(row, col, inventory.cols(), matrices, vertexConsumers, inventory.isLocked(), config.inventorySlotSize);
        }
        vertexConsumers.drawCurrentLayer();

        for (int i = 0; i < inventory.items().size(); i++) {
            ItemStack item = inventory.items().get(i);
            if (item.isEmpty()) continue;
            int row = i / inventory.cols();
            int col = i % inventory.cols();
            renderSlotItem(item, row, col, inventory.cols(), matrices, vertexConsumers, itemRenderQueue, inventory.world(), config.enableItemStackCountDisplay, config.inventorySlotSize, config.inventoryItemSize);
        }
        matrices.pop();
    }

    private record QueuedInventory(List<ItemStack> items, World world,
                                   double lerpedX, double lerpedY, double lerpedZ,
                                   int totalSlots, int cols, boolean isLocked) {
    }

    private static void renderSlotBackground(int row, int col, int cols,
                                             MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                             boolean isLocked, float slotSize) {
        matrices.push();
        double xOffset = (col - (cols - 1) / 2.0) * 0.5 * slotSize;
        double yOffset = row * 0.5 * slotSize + 1;
        matrices.translate(xOffset, yOffset, 0.0);

        VertexConsumer buffer = vertexConsumers.getBuffer(CustomRenderLayers.CUSTOM_BACKGROUND);
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        boolean changeColor = isLocked && MinecartVisualizerConfig.getInstance().enableHopperMinecartEnableDisplay;

        float r1 = changeColor ? 0.4f : 0.53f;
        float g1 = changeColor ? 0.23f : 0.53f;
        float b1 = changeColor ? 0.23f : 0.53f;

        float r2 = changeColor ? 0.6f : 0.9f;
        float g2 = changeColor ? 0.0f : 0.9f;
        float b2 = changeColor ? 0.0f : 0.9f;

        drawRect(matrix, buffer, 0.19f * slotSize, -0.06f * slotSize, r1, g1, b1, 0.25f);//背景
        drawRect(matrix, buffer, 0.22f * slotSize, -0.08f * slotSize, r2, g2, b2, 0.8f);//边框
        matrices.pop();
    }

    private static void renderSlotItem(ItemStack item, int row, int col, int cols,
                                       MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                       OrderedRenderCommandQueue itemRenderQueue, World world,
                                       boolean enableCount, float slotSize, float itemSize) {
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
        ItemModelManager itemModelManager = MinecraftClient.getInstance().getItemModelManager();

        matrices.push();
        double xOffset = (col - (cols - 1) / 2.0) * 0.5 * slotSize;
        double yOffset = row * 0.5 * slotSize + 1;
        matrices.translate(xOffset, yOffset, 0.0);

        matrices.push();
        if (item.getItem() instanceof BlockItem) {
            matrices.scale(-0.53f * itemSize, 0.53f * itemSize, 0.53f * itemSize);
        } else {
            matrices.scale(-0.4f * itemSize, 0.4f * itemSize, 0.4f * itemSize);
        }


        ItemRenderState renderState = new ItemRenderState();
        itemModelManager.clearAndUpdate(
                renderState,
                item,
                ItemDisplayContext.FIXED,
                world,
                null,
                0
        );

        if (!renderState.isEmpty()) {
            renderState.render(matrices, itemRenderQueue, 15728880, OverlayTexture.DEFAULT_UV, 0);
        }
        matrices.pop();

        if (enableCount && item.getCount() > 1) {
            String countString = String.valueOf(item.getCount());
            matrices.push();
            matrices.translate(0.12 * slotSize, -0.1 * slotSize, 0.1);
            matrices.scale(0.02f * itemSize, -0.02f * itemSize, 0.02f * itemSize);

            textRenderer.draw(countString, 0.0f, 0.0f, 0xFFFFFFFF, false,
                    matrices.peek().getPositionMatrix(), vertexConsumers,
                    TextRenderer.TextLayerType.SEE_THROUGH, 0, 15728880);
            matrices.pop();
        }

        matrices.pop();
    }



    public static void renderHopperRanges(Entity entity, double cameraX, double cameraY, double cameraZ,
                                          MatrixStack matrices, VertexConsumerProvider vertexConsumers) {
        VertexConsumer lines = vertexConsumers.getBuffer(CustomRenderLayers.CUSTOM_LINES);

        Box pickupBox = entity.getBoundingBox().expand(1, 0.4, 1);
        Box viewPickupBox = pickupBox.offset(-cameraX, -cameraY, -cameraZ);
        drawBox(matrices, lines, viewPickupBox, 1.0f, 1.0f, 0.1f, 0.8f);

        double minX = entity.getX() - 1;
        double minZ = entity.getZ() - 1;
        double minY = entity.getY() + 1;
        double maxX = entity.getX() + 1;
        double maxZ = entity.getZ() + 1;
        double maxY = entity.getY() + 4;

        Box extractionBox = new Box(minX, minY, minZ, maxX, maxY, maxZ);
        Box viewExtractionBox = extractionBox.offset(-cameraX, -cameraY, -cameraZ);
        drawBox(matrices, lines, viewExtractionBox, 1.0f, 1.0f, 0.1f, 0.8f);
    }

    public static boolean highlightExtractionTargets(Entity entity, double cameraX, double cameraY, double cameraZ,
                                                     MatrixStack matrices, VertexConsumerProvider vertexConsumers) {
        World world = entity.getEntityWorld();
        VertexConsumer lines = vertexConsumers.getBuffer(RenderLayers.LINES);
        boolean hasTarget = false;

        HopperMinecartEntity hopperMinecart = (HopperMinecartEntity) entity;

        Box extractionArea = new Box(
                entity.getX() - 0.5, entity.getY() + 1, entity.getZ() - 0.5,
                entity.getX() + 0.5, entity.getY() + 2, entity.getZ() + 0.5
        );

        //方块实体
        BlockPos targetPos = BlockPos.ofFloored(hopperMinecart.getHopperX(), hopperMinecart.getHopperY() + 1.0, hopperMinecart.getHopperZ());
        BlockState state = world.getBlockState(targetPos);

        if (world.getBlockEntity(targetPos) != null) {
            VoxelShape shape = state.getOutlineShape(world, targetPos);
            if (!shape.isEmpty()) {
                hasTarget = true;
                Box combinedBox = shape.getBoundingBox();
                Box viewBox = combinedBox.offset(
                        targetPos.getX() - cameraX,
                        targetPos.getY() - cameraY + 1.5,
                        targetPos.getZ() - cameraZ);

                drawScaledBox(matrices, lines, viewBox, 2f, 0.0f, 1.0f, 0.0f, 1.0f);
            }
        }

        // 实体
        List<Entity> inventories = world.getOtherEntities(entity, extractionArea, e ->
                e instanceof net.minecraft.entity.vehicle.VehicleInventory && e.isAlive()
        );

        if (!inventories.isEmpty()) {
            hasTarget = true;
            for (Entity invEntity : inventories) {
                Box viewBox = invEntity.getBoundingBox().offset(-cameraX, -cameraY + 1.5, -cameraZ);
                drawScaledBox(matrices, lines, viewBox, 2f, 0.0f, 1.0f, 0.0f, 1.0f);
            }
        }

        return hasTarget;
    }


    @Unique
    private static void drawScaledBox(MatrixStack matrices, VertexConsumer lines, Box viewBox, float scale, float r, float g, float b, float a) {
        matrices.push();

        double centerX = (viewBox.minX + viewBox.maxX) / 2.0;
        double centerY = (viewBox.minY + viewBox.maxY) / 2.0;
        double centerZ = (viewBox.minZ + viewBox.maxZ) / 2.0;

        matrices.translate(centerX, centerY, centerZ);
        matrices.scale(scale, scale, scale);

        Box centeredBox = new Box(
                viewBox.minX - centerX, viewBox.minY - centerY, viewBox.minZ - centerZ,
                viewBox.maxX - centerX, viewBox.maxY - centerY, viewBox.maxZ - centerZ
        );

        drawBox(matrices, lines, centeredBox, r, g, b, a);

        matrices.pop();
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

        lineConsumer.lineWidth(3.0f);

        Iterator<Vec3d> it = points.iterator();
        if (!it.hasNext()) return;

        Vec3d prevPoint = it.next().add(yOffset);

        while (it.hasNext()) {
            Vec3d currentPoint = it.next().add(yOffset);
            drawLine(prevPoint, currentPoint, matrix4f, lineConsumer, r, g, b);
            prevPoint = currentPoint;
        }

        lineConsumer.lineWidth(1.0f);
    }

    private static void drawRect(Matrix4f matrix, VertexConsumer buffer, float s, float z, float r, float g, float b, float a) {
        drawVertex(matrix, buffer, -s, -s, z, r, g, b, a);
        drawVertex(matrix, buffer, s, -s, z, r, g, b, a);
        drawVertex(matrix, buffer, s, s, z, r, g, b, a);
        drawVertex(matrix, buffer, -s, s, z, r, g, b, a);
    }

    private static void drawVertex(Matrix4f matrix, VertexConsumer buffer, float x, float y, float z, float r, float g, float b, float a) {
        buffer.vertex(matrix, x, y, z)
                .color(r, g, b, a)
                .texture(0.0f, 0.0f)
                .light(15728880)
                .normal(0.0f, 0.0f, 1.0f);
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

        drawBox(matrices, lines, standardBox.expand(0.005), r, g, b, a);
    }

    private static void drawBox(MatrixStack matrices, VertexConsumer vertexConsumer,
                                Box box, float red, float green, float blue, float alpha) {
        VoxelShape shape = VoxelShapes.cuboid(box);
        int color = ColorHelper.getArgb(
                (int)(alpha * 255),
                (int)(red * 255),
                (int)(green * 255),
                (int)(blue * 255)
        );
        VertexRendering.drawOutline(matrices, vertexConsumer, shape, 0.0, 0.0, 0.0, color, (float) 2.0);
    }
}



