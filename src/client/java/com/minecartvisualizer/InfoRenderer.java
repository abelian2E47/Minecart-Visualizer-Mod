package com.minecartvisualizer;

import com.minecartvisualizer.config.Colors;
import com.minecartvisualizer.config.MinecartVisualizerConfig;
import com.minecartvisualizer.tracker.HopperMinecartTracker;
import com.minecartvisualizer.tracker.TrackerColor;
import net.minecraft.block.BlockState;
import net.minecraft.block.InventoryProvider;
import net.minecraft.block.entity.Hopper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.item.ItemModelManager;
import net.minecraft.client.render.*;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.MutableText;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.World;
import net.minecraft.client.render.RenderLayers;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.*;

public class InfoRenderer {

    public static void renderTexts(List<MutableText> infoTexts, Entity entity, MatrixStack matrices, VertexConsumerProvider vertexConsumer, int textColor) {
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
        Matrix4f matrix4f = buildTextPose(entity, matrices);
        float startY = -(infoTexts.size() * 10);
        renderTextLayer(infoTexts, textRenderer, matrix4f, vertexConsumer, startY, true, textColor);
        renderTextLayer(infoTexts, textRenderer, matrix4f, vertexConsumer, startY, false, textColor);
    }

    private static Matrix4f buildTextPose(Entity entity, MatrixStack matrices) {
        var config = MinecartVisualizerConfig.getInstance();
        float baseHeight = entity.getHeight() + 0.5f;
        Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();
        matrices.push();
        matrices.translate(0.0, baseHeight, 0.0);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw() + 180));
        if (config.alwaysFacingThePlayer) {
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-camera.getPitch()));
        }
        matrices.scale(0.03f, -0.03f, 0.03f);
        Matrix4f pose = new Matrix4f(matrices.peek().getPositionMatrix());
        matrices.pop();
        return pose;
    }

    private static void renderTextLayer(List<MutableText> texts, TextRenderer renderer, Matrix4f matrix, VertexConsumerProvider vc, float y, boolean isBackground, int textColor) {
        float currentY = y;
        for (MutableText text : texts) {
            float x = -renderer.getWidth(text) / 2f;
            if (isBackground) {
                int backgroundColor = (textColor & 0xFFFFFF) | 0x4C000000;
                renderer.draw(text, x, currentY, backgroundColor, false, matrix, vc, TextRenderer.TextLayerType.SEE_THROUGH, 0x4CC8C8C8, 0xF000F0);
            } else {
                renderer.draw(text, x, currentY, textColor, false, matrix, vc, TextRenderer.TextLayerType.NORMAL, 0, 0xF000F0);
            }
            currentY += 10;
        }
    }

    private static final List<QueuedInventory> queuedInventories = new ArrayList<>();
    private static final List<QueuedExtractionTarget> queuedExtractionTargets = new ArrayList<>();
    private static final List<QueuedWorldBox> queuedWorldBoxes = new ArrayList<>();
    private static final List<QueuedInfoTexts> queuedInfoTexts = new ArrayList<>();

    public static void queueInfoTexts(List<MutableText> infoTexts, Entity entity, MatrixStack matrices, int textColor) {
        if (infoTexts.isEmpty()) {
            return;
        }
        queuedInfoTexts.add(new QueuedInfoTexts(List.copyOf(infoTexts), buildTextPose(entity, matrices), textColor));
    }

    public static void renderQueuedInfoTexts() {
        if (queuedInfoTexts.isEmpty()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer textRenderer = client.textRenderer;
        VertexConsumerProvider.Immediate vertexConsumers = client.getBufferBuilders().getEntityVertexConsumers();

        try {
            for (QueuedInfoTexts queued : queuedInfoTexts) {
                float startY = -(queued.texts().size() * 10);
                renderTextLayer(queued.texts(), textRenderer, queued.pose(), vertexConsumers, startY, true, queued.textColor());
                renderTextLayer(queued.texts(), textRenderer, queued.pose(), vertexConsumers, startY, false, queued.textColor());
            }

            vertexConsumers.draw();
        } finally {
            queuedInfoTexts.clear();
        }
    }

    public static void queueInventory(List<ItemStack> items, World world,
                                      double lerpedX, double lerpedY, double lerpedZ,
                                      int totalSlots, int cols, boolean isLocked) {
        if (totalSlots > 0) {
            queuedInventories.add(new QueuedInventory(items, world, lerpedX, lerpedY, lerpedZ, totalSlots, cols, isLocked));
        }
    }

    public static void beginTopRenderFrame() {
        queuedInventories.clear();
        queuedExtractionTargets.clear();
        queuedWorldBoxes.clear();
        queuedInfoTexts.clear();
    }

    public static boolean hasQueuedTopRenderContent() {
        return !queuedInventories.isEmpty() || !queuedExtractionTargets.isEmpty() || !queuedWorldBoxes.isEmpty()
                || !queuedInfoTexts.isEmpty();
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
    public static boolean renderQueuedExtractionTargets() {
        if (queuedExtractionTargets.isEmpty()) {
            return false;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        Vec3d cameraPos = client.gameRenderer.getCamera().getCameraPos();
        MatrixStack matrices = new MatrixStack();
        VertexConsumerProvider.Immediate vertexConsumers = client.getBufferBuilders().getEntityVertexConsumers();
        VertexConsumer lines = vertexConsumers.getBuffer(RenderLayers.LINES);

        float[] color = Colors.rgbFloats(MinecartVisualizerConfig.getInstance().extractionTargetColor, MinecartVisualizerConfig.DEFAULT_EXTRACTION_TARGET_COLOR.getRGB());
        int argb = ColorHelper.getArgb(255, (int) (color[0] * 255), (int) (color[1] * 255), (int) (color[2] * 255));
        for (QueuedExtractionTarget target : queuedExtractionTargets) {
            drawScaledOutline(matrices, lines, target.shape(),
                    target.origin().x - cameraPos.x, target.origin().y - cameraPos.y, target.origin().z - cameraPos.z,
                    target.scale(), argb);
        }

        vertexConsumers.draw();
        queuedExtractionTargets.clear();

        return true;
    }

    public static void renderQueuedWorldBoxes() {
        if (queuedWorldBoxes.isEmpty()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        Vec3d cameraPos = client.gameRenderer.getCamera().getCameraPos();
        MatrixStack matrices = new MatrixStack();
        VertexConsumerProvider.Immediate vertexConsumers = client.getBufferBuilders().getEntityVertexConsumers();
        VertexConsumer lines = vertexConsumers.getBuffer(RenderLayers.LINES);

        for (QueuedWorldBox queued : queuedWorldBoxes) {
            Box viewBox = queued.box().offset(-cameraPos.x, -cameraPos.y, -cameraPos.z);
            drawScaledBox(matrices, lines, viewBox, queued.scale(),
                    queued.color()[0], queued.color()[1], queued.color()[2], 1.0f);
        }

        vertexConsumers.draw();
        queuedWorldBoxes.clear();
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

    private record QueuedExtractionTarget(VoxelShape shape, Vec3d origin, float scale) {
    }

    private record QueuedWorldBox(Box box, float scale, float[] color) {
    }

    private record QueuedInfoTexts(List<MutableText> texts, Matrix4f pose, int textColor) {
    }

    private static void renderSlotBackground(int row, int col, int cols,
                                             MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                             boolean isLocked, float slotSize) {
        var config = MinecartVisualizerConfig.getInstance();
        matrices.push();
        double xOffset = (col - (cols - 1) / 2.0) * 0.5 * slotSize;
        double yOffset = row * 0.5 * slotSize + 1;
        matrices.translate(xOffset, yOffset, 0.0);

        VertexConsumer buffer = vertexConsumers.getBuffer(CustomRenderLayers.CUSTOM_BACKGROUND);
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        boolean changeColor = isLocked && config.enableHopperMinecartEnableDisplay;

        float[] background = Colors.rgbFloats(
                changeColor ? config.slotBackgroundLockedColor : config.slotBackgroundColor,
                changeColor ? MinecartVisualizerConfig.DEFAULT_SLOT_BACKGROUND_LOCKED_COLOR.getRGB()
                        : MinecartVisualizerConfig.DEFAULT_SLOT_BACKGROUND_COLOR.getRGB());
        float[] border = Colors.rgbFloats(
                changeColor ? config.slotBorderLockedColor : config.slotBorderColor,
                changeColor ? MinecartVisualizerConfig.DEFAULT_SLOT_BORDER_LOCKED_COLOR.getRGB()
                        : MinecartVisualizerConfig.DEFAULT_SLOT_BORDER_COLOR.getRGB());

        drawRect(matrix, buffer, 0.19f * slotSize, -0.06f * slotSize,
                background[0], background[1], background[2], 0.25f);//背景
        drawRect(matrix, buffer, 0.22f * slotSize, -0.08f * slotSize,
                border[0], border[1], border[2], 0.8f);//边框
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
            matrices.scale(0.53f * itemSize, 0.53f * itemSize, 0.53f * itemSize);
        } else {
            matrices.scale(0.4f * itemSize, 0.4f * itemSize, 0.4f * itemSize);
        }
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180));

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

            textRenderer.draw(countString, 0.0f, 0.0f,
                    Colors.rgb(MinecartVisualizerConfig.getInstance().itemCountTextColor, MinecartVisualizerConfig.DEFAULT_ITEM_COUNT_TEXT_COLOR.getRGB()) | 0xFF000000, false,
                    matrices.peek().getPositionMatrix(), vertexConsumers,
                    TextRenderer.TextLayerType.SEE_THROUGH, 0, 15728880);
            matrices.pop();
        }

        matrices.pop();
    }

    public static Box[] buildHopperRangeBoxes(Entity entity, Vec3d hopperPos) {
        Box pickupBox = entity.getBoundingBox().offset(
                hopperPos.x - entity.getX(), hopperPos.y - entity.getY(), hopperPos.z - entity.getZ()
        ).expand(0.25, 0.0, 0.25);

        Box inputAreaBox = Hopper.INPUT_AREA_SHAPE.offset(
                hopperPos.x - 0.5, hopperPos.y, hopperPos.z - 0.5);

        return new Box[]{pickupBox, inputAreaBox};
    }

    public static void renderHopperRanges(Entity entity, Vec3d hopperPos, double cameraX, double cameraY, double cameraZ,
                                          VertexConsumerProvider vertexConsumers,
                                          float[] pickupColor, float[] extractionColor, float scale) {
        VertexConsumer lines = vertexConsumers.getBuffer(CustomRenderLayers.CUSTOM_LINES);
        Box[] boxes = buildHopperRangeBoxes(entity, hopperPos);
        MatrixStack matrices = new MatrixStack();

        drawScaledBox(matrices, lines, boxes[0].offset(-cameraX, -cameraY, -cameraZ), scale,
                pickupColor[0], pickupColor[1], pickupColor[2], 0.8f);
        drawScaledBox(matrices, lines, boxes[1].offset(-cameraX, -cameraY, -cameraZ), scale,
                extractionColor[0], extractionColor[1], extractionColor[2], 0.8f);
    }

    //吸取范围框加入置顶渲染队列
    public static void queueWorldBox(Box box, float scale, float[] color) {
        queuedWorldBoxes.add(new QueuedWorldBox(box, scale, color));
    }

    public static boolean queueExtractionTargets(HopperMinecartDataPayload hopperData, float scale) {
        if (hopperData == null) {
            return false;
        }

        World world = MinecraftClient.getInstance().world;
        boolean hasTarget = false;

        Optional<BlockPos> extractionBlock = hopperData.extractionBlock();
        if (extractionBlock.isPresent() && world != null) {
            BlockPos targetPos = extractionBlock.get();
            BlockState state = world.getBlockState(targetPos);
            VoxelShape shape = state.getOutlineShape(world, targetPos);
            if (shape.isEmpty()) {
                shape = state.getCollisionShape(world, targetPos);
            }

            if (!shape.isEmpty()) {
                queuedExtractionTargets.add(new QueuedExtractionTarget(
                        shape, new Vec3d(targetPos.getX(), targetPos.getY(), targetPos.getZ()), scale));
                hasTarget = true;
            }
        }

        for (Box extractionBox : hopperData.extractionEntities()) {
            queuedExtractionTargets.add(new QueuedExtractionTarget(
                    VoxelShapes.cuboid(extractionBox), Vec3d.ZERO, scale));
            hasTarget = true;
        }

        return hasTarget;
    }

    private static void drawScaledOutline(MatrixStack matrices, VertexConsumer lines, VoxelShape shape,
                                          double offsetX, double offsetY, double offsetZ,
                                          float scale, int argb) {
        if (shape.isEmpty()) {
            return;
        }

        Box bounds = shape.getBoundingBox();
        double centerX = offsetX + (bounds.minX + bounds.maxX) / 2.0;
        double centerY = offsetY + (bounds.minY + bounds.maxY) / 2.0;
        double centerZ = offsetZ + (bounds.minZ + bounds.maxZ) / 2.0;

        matrices.push();
        matrices.translate(centerX, centerY, centerZ);
        matrices.scale(scale, scale, scale);
        matrices.translate(-centerX, -centerY, -centerZ);

        VertexRendering.drawOutline(matrices, lines, shape, offsetX, offsetY, offsetZ, argb, 2.0f);

        matrices.pop();
    }

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
        var config = MinecartVisualizerConfig.getInstance();

        float r, g, b;
        if (config.trackerPointUseDyeColor) {
            int hex = tracker.getTrackerColor().getHex();
            r = ((hex >> 16) & 0xFF) / 255f;
            g = ((hex >> 8) & 0xFF) / 255f;
            b = (hex & 0xFF) / 255f;
        } else {
            float[] rgb = Colors.rgbFloats(config.trackerTrailColor, MinecartVisualizerConfig.DEFAULT_TRACKER_TRAIL_COLOR.getRGB());
            r = rgb[0]; g = rgb[1]; b = rgb[2];
        }

        renderTrail(tracker.getTrailPoints(), matrices, lineConsumer, r, g, b);
    }

    public static void renderTrail(Collection<Vec3d> points,
                                   MatrixStack matrices, VertexConsumer lineConsumer,
                                   float r, float g, float b) {
        if (points.size() < 2) return;

        Matrix4f matrix4f = matrices.peek().getPositionMatrix();

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

        //LINES 的顶点格式包含 LineWidth 元素，必须每个顶点都写一次，否则 BufferBuilder 会抛
        //"Missing elements in vertex: LineWidth"（只在循环外设一次是不够的）。
        lineConsumer.vertex(matrix, startX, startY, startZ).color(r, g, b, 1.0f).normal(normal.x,normal.y,normal.z).lineWidth(3.0f);
        lineConsumer.vertex(matrix, endX, endY, endZ).color(r, g, b, 1.0f).normal(normal.x,normal.y,normal.z).lineWidth(3.0f);
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
        var config = MinecartVisualizerConfig.getInstance();

        float r, g, b, a;
        if (config.trackerPointUseDyeColor) {
            int hex = color.getHex();
            r = ((hex >> 16) & 0xFF) / 255.0f;
            g = ((hex >> 8) & 0xFF) / 255.0f;
            b = (hex & 0xFF) / 255.0f;
            a = active ? 1.0f : 0.6f;
            if (!active) {
                r *= 0.7f; g *= 0.7f; b *= 0.7f;
            }
        } else {
            float[] rgb = Colors.rgbFloats(
                    active ? config.trackerPointActiveColor : config.trackerPointInactiveColor,
                    active ? MinecartVisualizerConfig.DEFAULT_TRACKER_POINT_ACTIVE_COLOR.getRGB()
                            : MinecartVisualizerConfig.DEFAULT_TRACKER_POINT_INACTIVE_COLOR.getRGB());
            r = rgb[0]; g = rgb[1]; b = rgb[2];
            a = active ? 1.0f : 0.6f;
            if (!active) {
                r *= 0.7f; g *= 0.7f; b *= 0.7f;
            }
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
