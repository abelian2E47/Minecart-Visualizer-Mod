package com.minecartvisualizer;

import com.minecartvisualizer.config.Colors;
import com.minecartvisualizer.config.MinecartVisualizerConfig;
import com.minecartvisualizer.tracker.HopperMinecartTracker;
import com.minecartvisualizer.tracker.TrackerColor;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.Hopper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.*;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.item.ItemStack;
import net.minecraft.text.MutableText;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.World;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Optional;
import java.util.List;


public class InfoRenderer {
    public static boolean getCustomRenderLayer;

    public static void renderTexts(List<MutableText> infoTexts, Entity entity, MatrixStack matrices, VertexConsumerProvider vertexConsumer, int textColor) {
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
        Matrix4f matrix4f = buildTextPose(entity, matrices);

        float startY = -(infoTexts.size() * 10);

        renderTextLayer(infoTexts, textRenderer, matrix4f, vertexConsumer, startY, true, textColor);
        renderTextLayer(infoTexts, textRenderer, matrix4f, vertexConsumer, startY, false, textColor);
    }

    /**
     * 计算悬浮信息文本的最终姿态（相机相对坐标）。抽出来是为了让"置顶渲染"能在置顶阶段复用同一套变换，
     * 保证文本在两种渲染方式下的位置、朝向完全一致。
     */
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
                //背景层使用信息文本的颜色，但透明度较低
                int backgroundColor = (textColor & 0xFFFFFF) | 0x4C000000;
                renderer.draw(text, x, currentY, backgroundColor, false, matrix, vc, TextRenderer.TextLayerType.SEE_THROUGH, 0x4CC8C8C8, 0xF000F0);
            } else {
                renderer.draw(text, x, currentY, textColor, false, matrix, vc, TextRenderer.TextLayerType.NORMAL, 0, 0xF000F0);
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
        var config = MinecartVisualizerConfig.getInstance();
        ItemRenderer itemRenderer = MinecraftClient.getInstance().getItemRenderer();
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;

        matrices.push();

        double xOffset = (col - (cols - 1) / 2.0) * 0.5;
        double yOffset = row * 0.5;

        matrices.translate(xOffset, yOffset, 0.0);

        VertexConsumer buffer = immediate.getBuffer(RenderLayer.getGui());
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

        drawRect(matrix, buffer, 0.19f, -0.06f, background[0], background[1], background[2], 0.25f);//背景
        drawRect(matrix, buffer, 0.22f, -0.08f, border[0], border[1], border[2], 0.8f);//边框

        matrices.push();
        matrices.scale(0.38f, 0.38f, 0.38f);
        getCustomRenderLayer = true;
        itemRenderer.renderItem(item, ModelTransformationMode.GUI , 0xF000F0,
                OverlayTexture.DEFAULT_UV, matrices, immediate, null, 0);
        getCustomRenderLayer = false;
        matrices.pop();

        if (enableCount && item.getCount() > 1) {
            String countString = String.valueOf(item.getCount());
            matrices.push();
            matrices.translate(0.12, -0.1, 0.1);
            matrices.scale(0.017f, -0.017f, 0.017f);
            textRenderer.draw(countString, 0.0f, 0.0f,
                    Colors.rgb(config.itemCountTextColor, MinecartVisualizerConfig.DEFAULT_ITEM_COUNT_TEXT_COLOR.getRGB()) | 0xFF000000,
                    false, matrices.peek().getPositionMatrix(), immediate,
                    TextRenderer.TextLayerType.SEE_THROUGH, 0, 0xF000F0);
            matrices.pop();
        }

        matrices.pop();
    }

    // ------------------------------------------------------------------
    // 置顶渲染队列
    // ------------------------------------------------------------------

    private static final List<QueuedExtractionTarget> queuedExtractionTargets = new ArrayList<>();
    private static final List<QueuedWorldBox> queuedWorldBoxes = new ArrayList<>();
    private static final List<QueuedInfoTexts> queuedInfoTexts = new ArrayList<>();

    /**
     * 悬浮信息文本的置顶路径：开关开启时不直接画，而是连同当前姿态入队，
     * 由置顶阶段（清空深度之后）统一绘制，从而无视方块遮挡。
     */
    public static void queueInfoTexts(List<MutableText> infoTexts, Entity entity, MatrixStack matrices, int textColor) {
        if (infoTexts.isEmpty()) {
            return;
        }
        queuedInfoTexts.add(new QueuedInfoTexts(List.copyOf(infoTexts), buildTextPose(entity, matrices), textColor));
    }

    public static void renderQueuedInfoTexts() {
        if (queuedInfoTexts.isEmpty()) return;

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

    /**
     * 待置顶绘制的吸取对象轮廓。
     *
     * @param shape  轮廓形状，位于 {@code origin} 处的局部坐标系中
     * @param origin 形状局部坐标系原点的世界坐标
     * @param scale  以轮廓中心为基准的缩放
     */
    private record QueuedExtractionTarget(VoxelShape shape, Vec3d origin, float scale) {
    }

    /** 待置顶绘制的世界坐标框。 */
    private record QueuedWorldBox(Box box, float scale, float[] color) {
    }

    private record QueuedInfoTexts(List<MutableText> texts, Matrix4f pose, int textColor) {
    }

    /** 开始一帧的渲染：清空上一帧残留的置顶渲染内容。 */
    public static void beginTopRenderFrame() {
        queuedExtractionTargets.clear();
        queuedWorldBoxes.clear();
        queuedInfoTexts.clear();
    }

    public static boolean hasQueuedTopRenderContent() {
        return !queuedExtractionTargets.isEmpty() || !queuedWorldBoxes.isEmpty() || !queuedInfoTexts.isEmpty();
    }

    /** 把吸取范围框加入置顶渲染队列（在实体渲染阶段调用）。 */
    public static void queueWorldBox(Box box, float scale, float[] color) {
        queuedWorldBoxes.add(new QueuedWorldBox(box, scale, color));
    }

    /** 绘制并清空队列中的吸取范围框。 */
    public static void renderQueuedWorldBoxes() {
        if (queuedWorldBoxes.isEmpty()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        Vec3d cameraPos = client.gameRenderer.getCamera().getPos();
        VertexConsumerProvider.Immediate vertexConsumers = client.getBufferBuilders().getEntityVertexConsumers();
        //1.21.1 的 MAIN_TARGET 是空操作，这里显式切回主帧缓冲
        client.getFramebuffer().beginWrite(false);
        VertexConsumer lines = vertexConsumers.getBuffer(CustomRenderLayers.LINES_ON_TOP);
        MatrixStack matrices = new MatrixStack();

        for (QueuedWorldBox queued : queuedWorldBoxes) {
            Box viewBox = queued.box().offset(-cameraPos.x, -cameraPos.y, -cameraPos.z);
            drawScaledBox(matrices, lines, viewBox, queued.scale(),
                    queued.color()[0], queued.color()[1], queued.color()[2], 0.8f);
        }

        vertexConsumers.drawCurrentLayer();
        queuedWorldBoxes.clear();
    }

    /** 绘制并清空队列中的吸取对象轮廓。 */
    public static void renderQueuedExtractionTargets() {
        if (queuedExtractionTargets.isEmpty()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        Vec3d cameraPos = client.gameRenderer.getCamera().getPos();
        VertexConsumerProvider.Immediate vertexConsumers = client.getBufferBuilders().getEntityVertexConsumers();
        //1.21.1 的 MAIN_TARGET 是空操作，这里显式切回主帧缓冲
        client.getFramebuffer().beginWrite(false);
        VertexConsumer lines = vertexConsumers.getBuffer(CustomRenderLayers.LINES_ON_TOP);
        MatrixStack matrices = new MatrixStack();

        float[] color = Colors.rgbFloats(MinecartVisualizerConfig.getInstance().extractionTargetColor,
                MinecartVisualizerConfig.DEFAULT_EXTRACTION_TARGET_COLOR.getRGB());
        //1.21.1 的 ColorHelper.getArgb 参数顺序与后续版本不同，这里直接按位拼装
        int argb = 0xFF000000
                | ((int) (color[0] * 255) << 16)
                | ((int) (color[1] * 255) << 8)
                | (int) (color[2] * 255);

        RenderSystem.lineWidth(2.0f);
        for (QueuedExtractionTarget target : queuedExtractionTargets) {
            drawScaledOutline(matrices, lines, target.shape(),
                    target.origin().x - cameraPos.x, target.origin().y - cameraPos.y, target.origin().z - cameraPos.z,
                    target.scale(), argb);
        }
        RenderSystem.lineWidth(1.0f);

        vertexConsumers.drawCurrentLayer();
        queuedExtractionTargets.clear();
    }

    /**
     * 构建漏斗矿车的两个吸取范围框（世界坐标），元素 0 为掉落物吸取范围，元素 1 为上方输入区域。
     *
     * <p>两者都直接对应原版代码里实际用于搜索物品实体的区域：</p>
     * <ul>
     *     <li>{@code HopperMinecartEntity#canOperate()} 用
     *     {@code getBoundingBox().expand(0.25, 0.0, 0.25)} 搜索周围的掉落物；</li>
     *     <li>{@code HopperBlockEntity#getInputItemEntities()} 用
     *     {@code Hopper.INPUT_AREA_SHAPE.offset(hopperX - 0.5, hopperY - 0.5, hopperZ - 0.5)}，
     *     即 1×1、从 +11/16 格到 +2 格的柱体，搜索上方掉落的物品实体。</li>
     * </ul>
     *
     * @param entity   漏斗矿车（仅用于取碰撞箱尺寸）
     * @param hopperPos 服务端权威的矿车坐标，即原版 {@code getHopperX/Y/Z} 的 getX/getY/getZ
     */
    public static Box[] buildHopperRangeBoxes(Entity entity, Vec3d hopperPos) {
        Box pickupBox = entity.getBoundingBox().offset(
                hopperPos.x - entity.getX(), hopperPos.y - entity.getY(), hopperPos.z - entity.getZ()
        ).expand(0.25, 0.0, 0.25);

        // 漏斗矿车的 getHopperX/Y/Z 分别是 getX()、getY() + 0.5、getZ()
        Box inputAreaBox = Hopper.INPUT_AREA_SHAPE.offset(
                hopperPos.x - 0.5, hopperPos.y, hopperPos.z - 0.5);

        return new Box[]{pickupBox, inputAreaBox};
    }

    /** 直接在当前渲染流程中绘制吸取范围框（不置顶）。 */
    public static void renderHopperRanges(Entity entity, Vec3d hopperPos, double cameraX, double cameraY, double cameraZ,
                                          MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                          float[] pickupColor, float[] extractionColor, float scale) {
        VertexConsumer lines = vertexConsumers.getBuffer(RenderLayer.getLines());
        Box[] boxes = buildHopperRangeBoxes(entity, hopperPos);

        drawScaledBox(matrices, lines, boxes[0].offset(-cameraX, -cameraY, -cameraZ), scale,
                pickupColor[0], pickupColor[1], pickupColor[2], 0.8f);
        drawScaledBox(matrices, lines, boxes[1].offset(-cameraX, -cameraY, -cameraZ), scale,
                extractionColor[0], extractionColor[1], extractionColor[2], 0.8f);
    }

    /**
     * 高亮服务端算出的"这一 tick 真正会被吸取的对象"。
     *
     * <p>目标由服务端按原版 {@code HopperBlockEntity#extract(World, Hopper)} 的口径算好后
     * 随数据一起下发（方块：{@code BlockPos.ofFloored(hopperX, hopperY + 1.0, hopperZ)}；
     * 实体：以 {@code (hopperX, hopperY + 1.0, hopperZ)} 为中心、半径 0.5 的立方体内的容器实体）。
     * 客户端只负责画形状，不再用客户端世界去猜——客户端区块/实体同步可能滞后，
     * 远距离时甚至没有区块数据，猜出来的目标与真实吸取对象不一致。</p>
     *
     * <p>方块以自身的轮廓形状入队（与原版方块描边一致），因此形状不是完整方块的容器
     * （漏斗、堆肥桶、饰纹陶罐等）也能画出完整轮廓线。</p>
     *
     * @param hopperData 服务端下发的漏斗矿车数据（含吸取目标）
     * @param scale      以轮廓中心为基准的缩放
     * @return 是否至少高亮了一个吸取目标
     */
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
            // 与原版方块描边相同：优先使用轮廓形状，没有轮廓时退回碰撞形状
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
            // 实体的轮廓即其碰撞箱，形状坐标已是世界坐标，所以原点取零向量
            queuedExtractionTargets.add(new QueuedExtractionTarget(
                    VoxelShapes.cuboid(extractionBox), Vec3d.ZERO, scale));
            hasTarget = true;
        }

        return hasTarget;
    }

    /**
     * 绘制形状的完整轮廓线（遍历形状的每一条边），并以轮廓中心为基准缩放。
     */
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

        drawShapeOutline(matrices, lines, shape, offsetX, offsetY, offsetZ, argb);

        matrices.pop();
    }

    /** 以框的中心为基准缩放后绘制。 */
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

    /**
     * 渲染轨迹：默认沿用追踪器的染料颜色；
     * 当配置为不使用染料颜色（或未指定颜色）时，使用配置中的固定颜色。
     */
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

    /** 使用指定的固定颜色渲染轨迹。 */
    public static void renderTrail(Collection<Vec3d> points,
                                   MatrixStack matrices, VertexConsumer lineConsumer,
                                   float r, float g, float b) {
        if (points.size() < 2) return;

        Matrix4f matrix4f = matrices.peek().getPositionMatrix();

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
        var config = MinecartVisualizerConfig.getInstance();

        float r, g, b, a;
        if (config.trackerPointUseDyeColor) {
            int hex = color.getHex();
            r = ((hex >> 16) & 0xFF) / 255.0f;
            g = ((hex >> 8) & 0xFF) / 255.0f;
            b = (hex & 0xFF) / 255.0f;
        } else {
            float[] rgb = Colors.rgbFloats(
                    active ? config.trackerPointActiveColor : config.trackerPointInactiveColor,
                    active ? MinecartVisualizerConfig.DEFAULT_TRACKER_POINT_ACTIVE_COLOR.getRGB()
                            : MinecartVisualizerConfig.DEFAULT_TRACKER_POINT_INACTIVE_COLOR.getRGB());
            r = rgb[0]; g = rgb[1]; b = rgb[2];
        }

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

    /** 1.21.1 没有 VertexRendering，这里按同样的方式绘制形状轮廓。 */
    private static void drawShapeOutline(MatrixStack matrices, VertexConsumer lines, VoxelShape shape,
                                         double offsetX, double offsetY, double offsetZ, int argb) {
        MatrixStack.Entry entry = matrices.peek();
        shape.forEachEdge((x1, y1, z1, x2, y2, z2) -> {
            Vector3f normal = new Vector3f((float) (x2 - x1), (float) (y2 - y1), (float) (z2 - z1)).normalize();
            lines.vertex(entry, (float) (x1 + offsetX), (float) (y1 + offsetY), (float) (z1 + offsetZ))
                    .color(argb).normal(entry, normal.x(), normal.y(), normal.z());
            lines.vertex(entry, (float) (x2 + offsetX), (float) (y2 + offsetY), (float) (z2 + offsetZ))
                    .color(argb).normal(entry, normal.x(), normal.y(), normal.z());
        });
    }

    private static void drawBox(MatrixStack matrices, VertexConsumer vertexConsumer,
                                Box box, float red, float green, float blue, float alpha) {
        WorldRenderer.drawBox(matrices, vertexConsumer, box, red, green, blue, alpha);
    }

    /**
     * 取矿车的服务端权威坐标（两个服务端 tick 之间按渲染 tick 插值）。
     *
     * <p>没有收到服务端坐标（例如服务端未安装本模组，或同步尚未到达）时返回 {@code null}，
     * 调用方应回退到客户端的插值坐标。</p>
     */
    public static Vec3d getAuthoritativePos(Entity entity, float tickDelta) {
        Vec3d serverPos = MinecartClientHandler.getServerPos(entity.getUuid(), tickDelta);
        if (serverPos == null) {
            return null;
        }

        // 传送等造成的短暂失步：位置差得太多时以客户端坐标为准，避免框停留在旧位置
        double clientX = MathHelper.lerp(tickDelta, entity.lastRenderX, entity.getX());
        double clientY = MathHelper.lerp(tickDelta, entity.lastRenderY, entity.getY());
        double clientZ = MathHelper.lerp(tickDelta, entity.lastRenderZ, entity.getZ());
        if (serverPos.squaredDistanceTo(clientX, clientY, clientZ) > 64.0) {
            return null;
        }

        return serverPos;
    }

    /** 服务端坐标不可用时的回退：客户端插值坐标。 */
    public static Vec3d getClientPos(Entity entity, float tickDelta) {
        return new Vec3d(
                MathHelper.lerp(tickDelta, entity.lastRenderX, entity.getX()),
                MathHelper.lerp(tickDelta, entity.lastRenderY, entity.getY()),
                MathHelper.lerp(tickDelta, entity.lastRenderZ, entity.getZ())
        );
    }
}
