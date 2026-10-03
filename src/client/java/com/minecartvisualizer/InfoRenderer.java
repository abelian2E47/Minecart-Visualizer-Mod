package com.minecartvisualizer;

import com.minecartvisualizer.config.Colors;
import com.minecartvisualizer.config.MinecartVisualizerConfig;
import com.minecartvisualizer.tracker.HopperMinecartTracker;
import com.minecartvisualizer.tracker.TrackerColor;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.Hopper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.*;

public class InfoRenderer {

    public static void renderTexts(List<MutableComponent> infoTexts, Entity entity, PoseStack matrices, SubmitNodeCollector collector, int textColor) {
        Font textRenderer = Minecraft.getInstance().font;
        PoseStack pose = poseOf(buildTextPose(entity, matrices));
        float startY = -(infoTexts.size() * 10);
        renderTextLayer(infoTexts, textRenderer, pose, collector, startY, true, textColor);
        renderTextLayer(infoTexts, textRenderer, pose, collector, startY, false, textColor);
    }

    private static Matrix4f buildTextPose(Entity entity, PoseStack matrices) {
        var config = MinecartVisualizerConfig.getInstance();
        float baseHeight = entity.getBbHeight() + 0.5f;
        Camera camera = Minecraft.getInstance().gameRenderer.mainCamera();
        matrices.pushPose();
        matrices.translate(0.0, baseHeight, 0.0);
        matrices.mulPose(Axis.YP.rotationDegrees(-camera.yRot() + 180));
        if (config.alwaysFacingThePlayer) {
            matrices.mulPose(Axis.XP.rotationDegrees(-camera.xRot()));
        }
        matrices.scale(0.03f, -0.03f, 0.03f);
        Matrix4f pose = new Matrix4f(matrices.last().pose());
        matrices.popPose();
        return pose;
    }

    private static PoseStack poseOf(Matrix4f matrix) {
        PoseStack stack = new PoseStack();
        stack.mulPose(matrix);
        return stack;
    }

    private static void renderTextLayer(List<MutableComponent> texts, Font renderer, PoseStack matrices, SubmitNodeCollector collector,
                                        float y, boolean isBackground, int textColor) {
        float currentY = y;
        for (MutableComponent text : texts) {
            float x = -renderer.width(text) / 2f;
            if (isBackground) {
                int backgroundColor = (textColor & 0xFFFFFF) | 0x4C000000;
                collector.submitText(matrices, x, currentY, text.getVisualOrderText(), false,
                        Font.DisplayMode.SEE_THROUGH, 0xF000F0, backgroundColor, 0x4CC8C8C8, 0);
            } else {
                collector.submitText(matrices, x, currentY, text.getVisualOrderText(), false,
                        Font.DisplayMode.NORMAL, 0xF000F0, textColor, 0, 0);
            }
            currentY += 10;
        }
    }

    private static final List<QueuedInventory> queuedInventories = new ArrayList<>();
    private static final List<QueuedExtractionTarget> queuedExtractionTargets = new ArrayList<>();
    private static final List<QueuedWorldBox> queuedWorldBoxes = new ArrayList<>();
    private static final List<QueuedInfoTexts> queuedInfoTexts = new ArrayList<>();

    public static void queueInfoTexts(List<MutableComponent> infoTexts, Entity entity, PoseStack matrices, int textColor) {
        if (infoTexts.isEmpty()) {
            return;
        }
        queuedInfoTexts.add(new QueuedInfoTexts(List.copyOf(infoTexts), buildTextPose(entity, matrices), textColor));
    }

    public static void renderQueuedInfoTexts(SubmitNodeCollector collector) {
        if (queuedInfoTexts.isEmpty()) {
            return;
        }

        Font textRenderer = Minecraft.getInstance().font;

        try {
            for (QueuedInfoTexts queued : queuedInfoTexts) {
                PoseStack pose = poseOf(queued.pose());
                float startY = -(queued.texts().size() * 10);
                renderTextLayer(queued.texts(), textRenderer, pose, collector, startY, true, queued.textColor());
                renderTextLayer(queued.texts(), textRenderer, pose, collector, startY, false, queued.textColor());
            }
        } finally {
            queuedInfoTexts.clear();
        }
    }

    public static void queueInventory(List<ItemStack> items, Level world,
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

    public static void renderQueuedInventories(SubmitNodeCollector collector) {
        if (queuedInventories.isEmpty()) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        Vec3 cameraPos = client.gameRenderer.mainCamera().position();
        PoseStack matrices = new PoseStack();

        try {
            for (QueuedInventory inventory : queuedInventories) {
                renderInventory(inventory, cameraPos, matrices, collector);
            }
        } finally {
            queuedInventories.clear();
        }

    }
    public static boolean renderQueuedExtractionTargets(SubmitNodeCollector collector) {
        if (queuedExtractionTargets.isEmpty()) {
            return false;
        }

        Minecraft client = Minecraft.getInstance();
        Vec3 cameraPos = client.gameRenderer.mainCamera().position();
        PoseStack matrices = new PoseStack();

        float[] color = Colors.rgbFloats(MinecartVisualizerConfig.getInstance().extractionTargetColor, MinecartVisualizerConfig.DEFAULT_EXTRACTION_TARGET_COLOR.getRGB());
        int argb = ARGB.color(255, (int) (color[0] * 255), (int) (color[1] * 255), (int) (color[2] * 255));
        for (QueuedExtractionTarget target : queuedExtractionTargets) {
            drawScaledOutline(matrices, collector, target.shape(),
                    target.origin().x - cameraPos.x, target.origin().y - cameraPos.y, target.origin().z - cameraPos.z,
                    target.scale(), argb);
        }

        queuedExtractionTargets.clear();

        return true;
    }

    public static void renderQueuedWorldBoxes(SubmitNodeCollector collector) {
        if (queuedWorldBoxes.isEmpty()) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        Vec3 cameraPos = client.gameRenderer.mainCamera().position();
        PoseStack matrices = new PoseStack();

        for (QueuedWorldBox queued : queuedWorldBoxes) {
            AABB viewBox = queued.box().move(-cameraPos.x, -cameraPos.y, -cameraPos.z);
            drawScaledBox(matrices, collector, viewBox, queued.scale(),
                    queued.color()[0], queued.color()[1], queued.color()[2], 1.0f);
        }

        queuedWorldBoxes.clear();
    }

    private static void renderInventory(QueuedInventory inventory, Vec3 cameraPos,
                                        PoseStack matrices, SubmitNodeCollector collector) {
        var config = MinecartVisualizerConfig.getInstance();
        Camera camera = Minecraft.getInstance().gameRenderer.mainCamera();
        matrices.pushPose();
        matrices.translate(inventory.lerpedX() - cameraPos.x, inventory.lerpedY() + 0.4 - cameraPos.y, inventory.lerpedZ() - cameraPos.z);
        matrices.mulPose(Axis.YP.rotationDegrees(-camera.yRot() + 180));
        if (config.alwaysFacingThePlayer) {
            matrices.mulPose(Axis.XP.rotationDegrees(-camera.xRot()));
        }

        for (int i = 0; i < inventory.totalSlots(); i++) {
            int row = i / inventory.cols();
            int col = i % inventory.cols();
            renderSlotBackground(row, col, inventory.cols(), matrices, collector, inventory.isLocked(), config.inventorySlotSize);
        }

        for (int i = 0; i < inventory.items().size(); i++) {
            ItemStack item = inventory.items().get(i);
            if (item.isEmpty()) continue;
            int row = i / inventory.cols();
            int col = i % inventory.cols();
            renderSlotItem(item, row, col, inventory.cols(), matrices, collector, inventory.world(), config.enableItemStackCountDisplay, config.inventorySlotSize, config.inventoryItemSize);
        }
        matrices.popPose();
    }

    private record QueuedInventory(List<ItemStack> items, Level world,
                                   double lerpedX, double lerpedY, double lerpedZ,
                                   int totalSlots, int cols, boolean isLocked) {
    }

    private record QueuedExtractionTarget(VoxelShape shape, Vec3 origin, float scale) {
    }

    private record QueuedWorldBox(AABB box, float scale, float[] color) {
    }

    private record QueuedInfoTexts(List<MutableComponent> texts, Matrix4f pose, int textColor) {
    }

    private static void renderSlotBackground(int row, int col, int cols,
                                             PoseStack matrices, SubmitNodeCollector collector,
                                             boolean isLocked, float slotSize) {
        var config = MinecartVisualizerConfig.getInstance();
        matrices.pushPose();
        double xOffset = (col - (cols - 1) / 2.0) * 0.5 * slotSize;
        double yOffset = row * 0.5 * slotSize + 1;
        matrices.translate(xOffset, yOffset, 0.0);

        boolean changeColor = isLocked && config.enableHopperMinecartEnableDisplay;

        float[] background = Colors.rgbFloats(
                changeColor ? config.slotBackgroundLockedColor : config.slotBackgroundColor,
                changeColor ? MinecartVisualizerConfig.DEFAULT_SLOT_BACKGROUND_LOCKED_COLOR.getRGB()
                        : MinecartVisualizerConfig.DEFAULT_SLOT_BACKGROUND_COLOR.getRGB());
        float[] border = Colors.rgbFloats(
                changeColor ? config.slotBorderLockedColor : config.slotBorderColor,
                changeColor ? MinecartVisualizerConfig.DEFAULT_SLOT_BORDER_LOCKED_COLOR.getRGB()
                        : MinecartVisualizerConfig.DEFAULT_SLOT_BORDER_COLOR.getRGB());

        collector.submitCustomGeometry(matrices, CustomRenderLayers.CUSTOM_BACKGROUND, (pose, buffer) -> {
            drawRect(pose.pose(), buffer, 0.19f * slotSize, -0.06f * slotSize,
                    background[0], background[1], background[2], 0.25f);//背景
            drawRect(pose.pose(), buffer, 0.22f * slotSize, -0.08f * slotSize,
                    border[0], border[1], border[2], 0.8f);//边框
        });
        matrices.popPose();
    }

    private static void renderSlotItem(ItemStack item, int row, int col, int cols,
                                       PoseStack matrices, SubmitNodeCollector collector, Level world,
                                       boolean enableCount, float slotSize, float itemSize) {
        Font textRenderer = Minecraft.getInstance().font;
        ItemModelResolver itemModelManager = Minecraft.getInstance().getItemModelResolver();

        matrices.pushPose();
        double xOffset = (col - (cols - 1) / 2.0) * 0.5 * slotSize;
        double yOffset = row * 0.5 * slotSize + 1;
        matrices.translate(xOffset, yOffset, 0.0);

        matrices.pushPose();
        if (item.getItem() instanceof BlockItem) {
            matrices.scale(0.53f * itemSize, 0.53f * itemSize, 0.53f * itemSize);
        } else {
            matrices.scale(0.4f * itemSize, 0.4f * itemSize, 0.4f * itemSize);
        }
        matrices.mulPose(Axis.YP.rotationDegrees(180));

        ItemStackRenderState renderState = new ItemStackRenderState();
        itemModelManager.updateForTopItem(
                renderState,
                item,
                ItemDisplayContext.FIXED,
                world,
                null,
                0
        );

        if (!renderState.isEmpty()) {
            renderState.submit(matrices, collector, 15728880, OverlayTexture.NO_OVERLAY, 0);
        }
        matrices.popPose();

        if (enableCount && item.getCount() > 1) {
            String countString = String.valueOf(item.getCount());
            matrices.pushPose();
            matrices.translate(0.12 * slotSize, -0.1 * slotSize, 0.1);
            matrices.scale(0.02f * itemSize, -0.02f * itemSize, 0.02f * itemSize);

            collector.submitText(matrices, 0.0f, 0.0f,
                    FormattedCharSequence.forward(countString, Style.EMPTY), false, Font.DisplayMode.SEE_THROUGH, 15728880,
                    Colors.rgb(MinecartVisualizerConfig.getInstance().itemCountTextColor, MinecartVisualizerConfig.DEFAULT_ITEM_COUNT_TEXT_COLOR.getRGB()) | 0xFF000000,
                    0, 0);
            matrices.popPose();
        }

        matrices.popPose();
    }

    public static AABB[] buildHopperRangeBoxes(Entity entity, Vec3 hopperPos) {
        AABB pickupBox = entity.getBoundingBox().move(
                hopperPos.x - entity.getX(), hopperPos.y - entity.getY(), hopperPos.z - entity.getZ()
        ).inflate(0.25, 0.0, 0.25);

        AABB inputAreaBox = Hopper.SUCK_AABB.move(
                hopperPos.x - 0.5, hopperPos.y, hopperPos.z - 0.5);

        return new AABB[]{pickupBox, inputAreaBox};
    }

    public static void renderHopperRanges(Entity entity, Vec3 hopperPos, double cameraX, double cameraY, double cameraZ,
                                          SubmitNodeCollector collector,
                                          float[] pickupColor, float[] extractionColor, float scale) {
        AABB[] boxes = buildHopperRangeBoxes(entity, hopperPos);
        PoseStack matrices = new PoseStack();

        for (int i = 0; i < boxes.length; i++) {
            float[] color = i == 0 ? pickupColor : extractionColor;
            AABB viewBox = boxes[i].move(-cameraX, -cameraY, -cameraZ);
            drawScaledBox(matrices, collector, viewBox, scale, color[0], color[1], color[2], 0.8f);
        }
    }

    //吸取范围框加入置顶渲染队列
    public static void queueWorldBox(AABB box, float scale, float[] color) {
        queuedWorldBoxes.add(new QueuedWorldBox(box, scale, color));
    }

    public static boolean queueExtractionTargets(HopperMinecartDataPayload hopperData, float scale) {
        if (hopperData == null) {
            return false;
        }

        Level world = Minecraft.getInstance().level;
        boolean hasTarget = false;

        Optional<BlockPos> extractionBlock = hopperData.extractionBlock();
        if (extractionBlock.isPresent() && world != null) {
            BlockPos targetPos = extractionBlock.get();
            BlockState state = world.getBlockState(targetPos);
            VoxelShape shape = state.getShape(world, targetPos);
            if (shape.isEmpty()) {
                shape = state.getCollisionShape(world, targetPos);
            }

            if (!shape.isEmpty()) {
                queuedExtractionTargets.add(new QueuedExtractionTarget(
                        shape, new Vec3(targetPos.getX(), targetPos.getY(), targetPos.getZ()), scale));
                hasTarget = true;
            }
        }

        for (AABB extractionBox : hopperData.extractionEntities()) {
            queuedExtractionTargets.add(new QueuedExtractionTarget(
                    Shapes.create(extractionBox), Vec3.ZERO, scale));
            hasTarget = true;
        }

        return hasTarget;
    }

    private static void drawScaledOutline(PoseStack matrices, SubmitNodeCollector collector, VoxelShape shape,
                                          double offsetX, double offsetY, double offsetZ,
                                          float scale, int argb) {
        if (shape.isEmpty()) {
            return;
        }

        AABB bounds = shape.bounds();
        double centerX = offsetX + (bounds.minX + bounds.maxX) / 2.0;
        double centerY = offsetY + (bounds.minY + bounds.maxY) / 2.0;
        double centerZ = offsetZ + (bounds.minZ + bounds.maxZ) / 2.0;

        matrices.pushPose();
        matrices.translate(centerX, centerY, centerZ);
        matrices.scale(scale, scale, scale);
        matrices.translate(-centerX, -centerY, -centerZ);
        matrices.translate(offsetX, offsetY, offsetZ);

        collector.submitShapeOutline(matrices, shape, CustomRenderLayers.CUSTOM_LINES, argb, 2.0f, false);

        matrices.popPose();
    }

    private static void drawScaledBox(PoseStack matrices, SubmitNodeCollector collector, AABB viewBox, float scale, float r, float g, float b, float a) {
        matrices.pushPose();

        double centerX = (viewBox.minX + viewBox.maxX) / 2.0;
        double centerY = (viewBox.minY + viewBox.maxY) / 2.0;
        double centerZ = (viewBox.minZ + viewBox.maxZ) / 2.0;

        matrices.translate(centerX, centerY, centerZ);
        matrices.scale(scale, scale, scale);

        AABB centeredBox = new AABB(
                viewBox.minX - centerX, viewBox.minY - centerY, viewBox.minZ - centerZ,
                viewBox.maxX - centerX, viewBox.maxY - centerY, viewBox.maxZ - centerZ
        );

        drawBox(matrices, collector, centeredBox, r, g, b, a);

        matrices.popPose();
    }

    public static void renderTrail(HopperMinecartTracker tracker,
                                   PoseStack matrices, SubmitNodeCollector collector) {
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

        renderTrail(tracker.getTrailPoints(), matrices, collector, r, g, b);
    }

    public static void renderTrail(Collection<Vec3> points,
                                   PoseStack matrices, SubmitNodeCollector collector,
                                   float r, float g, float b) {
        if (points.size() < 2) return;

        collector.submitCustomGeometry(matrices, CustomRenderLayers.CUSTOM_LINES, (pose, lineConsumer) -> {
            Vec3 yOffset = new Vec3(0, 0.5, 0);

            lineConsumer.setLineWidth(3.0f);

            Iterator<Vec3> it = points.iterator();
            if (!it.hasNext()) return;

            Vec3 prevPoint = it.next().add(yOffset);

            while (it.hasNext()) {
                Vec3 currentPoint = it.next().add(yOffset);
                drawLine(prevPoint, currentPoint, pose.pose(), lineConsumer, r, g, b);
                prevPoint = currentPoint;
            }

            lineConsumer.setLineWidth(1.0f);
        });
    }

    private static void drawRect(Matrix4f matrix, VertexConsumer buffer, float s, float z, float r, float g, float b, float a) {
        drawVertex(matrix, buffer, -s, -s, z, r, g, b, a);
        drawVertex(matrix, buffer, s, -s, z, r, g, b, a);
        drawVertex(matrix, buffer, s, s, z, r, g, b, a);
        drawVertex(matrix, buffer, -s, s, z, r, g, b, a);
    }

    private static void drawVertex(Matrix4f matrix, VertexConsumer buffer, float x, float y, float z, float r, float g, float b, float a) {
        buffer.addVertex(matrix, x, y, z)
                .setColor(r, g, b, a)
                .setUv(0.0f, 0.0f)
                .setLight(15728880)
                .setNormal(0.0f, 0.0f, 1.0f);
    }

    public static void drawLine(Vec3 startPoint, Vec3 endPoint,
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
        lineConsumer.addVertex(matrix, startX, startY, startZ).setColor(r, g, b, 1.0f).setNormal(normal.x,normal.y,normal.z).setLineWidth(3.0f);
        lineConsumer.addVertex(matrix, endX, endY, endZ).setColor(r, g, b, 1.0f).setNormal(normal.x,normal.y,normal.z).setLineWidth(3.0f);
    }

    public static boolean shouldRender(Entity entity) {
        var config = MinecartVisualizerConfig.getInstance();

        if (!config.enableMinecartVisualization) {
            return false;
        }

        Player player = Minecraft.getInstance().player;
        if (player != null && entity.distanceToSqr(player) > config.infoRenderDistance * config.infoRenderDistance) {
            return false;
        }

        return !config.mergeStackingMinecartInfo || MinecartClientHandler.isLeader(entity.getUUID());
    }

    public static List<MutableComponent> getInfoTexts(MinecartDataPayload displayInfo){
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

    public static List<MutableComponent> getTNTMinecartInfoTexts(TNTMinecartDataPayload displayInfo){
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

    public static void drawTrackerPointBox(PoseStack matrices, SubmitNodeCollector collector, TrackerColor color,
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

        AABB standardBox = new AABB(minX, minY, minZ, minX + 1.0, minY + 1.0, minZ + 1.0);

        drawBox(matrices, collector, standardBox.inflate(0.005), r, g, b, a);
    }

    private static void drawBox(PoseStack matrices, SubmitNodeCollector collector,
                                AABB box, float red, float green, float blue, float alpha) {
        VoxelShape shape = Shapes.create(box);
        int color = ARGB.color(
                (int)(alpha * 255),
                (int)(red * 255),
                (int)(green * 255),
                (int)(blue * 255)
        );
        collector.submitShapeOutline(matrices, shape, CustomRenderLayers.CUSTOM_LINES, color, (float) 2.0, false);
    }
}
