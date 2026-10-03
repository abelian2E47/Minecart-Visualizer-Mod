    package com.minecartvisualizer.tracker;

    import com.minecartvisualizer.MinecartDataPayload;
    import com.minecartvisualizer.MinecartClientHandler;
    import com.minecartvisualizer.MinecartCollisionPayload;
    import com.minecartvisualizer.config.MinecartVisualizerConfig;
import java.util.*;
    import java.util.concurrent.ConcurrentLinkedDeque;
import net.minecraft.ChatFormatting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

    public class HopperMinecartTracker {
        private final TrackerColor trackerColor;

        private final Deque<Vec3> trailPoints = new ConcurrentLinkedDeque<>();
        private Vec3 lastPoint = null;
        private Vec3 nextLastPoint = null;

        private boolean removed = false;
        private final String shortUuid;
        private final UUID uuid;
        private final int id;
        private final LocalPlayer player;
        //追踪开始时的服务端时间（tick）；运行时长按服务端 tick 计，客户端卡顿/暂停不会让它漂移
        private final long trackStartServerTime;
        private long lastKnownServerTime;
        private long runTime;
        private long lastTrailServerTime = -1L;
        //最近一次确认数据仍然新鲜的时刻（毫秒），用于判断是不是彻底失联
        private long lastFreshMs;
        private Vec3 leastPos = null;
        private List<ItemStack> lastInv = null;
        private final TrackerFilter filter;

        private long firstChangeTick = -1, lastChangeTick = -1;
        //开始连续吸取时物品栏
        private List<ItemStack> preChangeInv = null;
        //开始连续吸取时坐标
        private Vec3 startPos = null;
        //最近一次输出挤压消息时的服务端时间
        private long lastCollisionMsgTime = -1L;

        public HopperMinecartTracker(TrackerColor trackerColor, UUID uuid, LocalPlayer player, int id){
            this.id = id;
            this.uuid = uuid;
            this.shortUuid = uuid.toString().substring(0, 4);
            this.trackerColor = trackerColor;
            this.player = player;
            trackStartServerTime = MinecartClientHandler.getLatestServerTime();
            lastKnownServerTime = Math.max(0L, trackStartServerTime);
            lastFreshMs = MinecartClientHandler.nowMs();
            this.filter = TrackersManager.filters.get(trackerColor);
            runTime = 0;
            this.tick();
        }

        public void tick() {
            var config = MinecartVisualizerConfig.getInstance();
            handleCollisions(config);

            //服务端明确通知销毁：这是唯一的"矿车被摧毁"判据
            MinecartClientHandler.RemovalNotice removal = MinecartClientHandler.consumeRemoval(uuid);
            if (removal != null) {
                handleRemoval(removal, config);
                return;
            }

            var minecartData = MinecartClientHandler.getFreshMinecartData(uuid);
            var hopperData = MinecartClientHandler.getFreshHopperMinecartData(uuid);

            if (minecartData == null || hopperData == null) {
                //没数据不等于被摧毁：可能只是走远、区块没加载、或者服务端刚好没广播。
                //这里保持追踪器存活但停止统计，彻底失联很久后静默放弃（不发"被摧毁"的误报）。
                if (MinecartClientHandler.nowMs() - lastFreshMs > MinecartClientHandler.LOST_CONTACT_MS) {
                    TrackersManager.getCounter(trackerColor).recordTrackerRemoval((int) runTime);
                    this.removed = true;
                }
                return;
            }

            long currentTime = minecartData.serverTime();
            lastKnownServerTime = currentTime;
            lastFreshMs = MinecartClientHandler.nowMs();
            runTime = currentTime - trackStartServerTime;

            this.leastPos = minecartData.pos();
            List<ItemStack> currentInv = hopperData.items();

            Vec3 currentPos = minecartData.pos();

            //轨迹记录：按服务端 tick 采样（同一服务端 tick 只记一个点），而不是按客户端 tick
            if (config.trackMinecartTrail && currentTime != lastTrailServerTime) {
                lastTrailServerTime = currentTime;
                updateTrail(currentPos, config.maxTrailPoints);
            }

            //物品栏更改则记录
            boolean changed = hasInventoryChanged(currentInv);
            if (lastInv != null) {
                if (changed) {
                    if (firstChangeTick == -1) {
                        firstChangeTick = currentTime;
                        startPos = this.leastPos;
                        preChangeInv = copyInventory(lastInv);
                    }
                    lastChangeTick = currentTime;
                } else if (firstChangeTick != -1) {
                    recordCounterStats(lastInv, RecordType.INVENTORY_CHANGE, preChangeInv);
                    if (config.outputWhenSlotChange){
                        sendInventoryMessage(currentInv);
                    }
                }
            }
            if (changed || lastInv == null) {
                this.lastInv = copyInventory(currentInv);
            }
        }

        private void handleRemoval(MinecartClientHandler.RemovalNotice removal, MinecartVisualizerConfig config) {
            MinecartDataPayload finalData = removal.data();
            if (finalData != null) {
                this.leastPos = finalData.pos();
                lastKnownServerTime = Math.max(lastKnownServerTime, finalData.serverTime());
                runTime = lastKnownServerTime - trackStartServerTime;
            }

            List<ItemStack> finalInv = lastInv;
            if (removal.hopper() != null) {
                finalInv = removal.hopper().items();
            }

            if (firstChangeTick != -1) {
                sendInventoryMessage(finalInv);
            }

            if (config.outputWhenDestroyed) {
                sendDestroyedMessage(finalInv);
            }
            recordCounterStats(finalInv, RecordType.DROPS, null);
            TrackersManager.getCounter(trackerColor).recordTrackerRemoval((int) runTime);

            this.removed = true;
        }

        private void handleCollisions(MinecartVisualizerConfig config) {
            if (!config.outputOnCollision) {
                //关闭时也要把队列排空，否则重新打开会把关闭期间的历史事件一次性涌出来
                while (MinecartClientHandler.pollCollision(uuid) != null) {
                    //丢弃
                }
                return;
            }

            MinecartCollisionPayload collision;
            while ((collision = MinecartClientHandler.pollCollision(uuid)) != null) {
                if (collision.deltaMomentum() < config.collisionMomentumThreshold) continue;

                long now = MinecartClientHandler.getLatestServerTime();
                if (config.collisionMessageCooldown > 0
                        && lastCollisionMsgTime >= 0
                        && now - lastCollisionMsgTime < config.collisionMessageCooldown) {
                    continue;
                }
                lastCollisionMsgTime = now;

                sendCollisionMessage(collision, config);
            }
        }

        //矿车被挤压（碰撞）消息：事件数据全部来自服务端，这里只负责挑选字段与排版
        private void sendCollisionMessage(MinecartCollisionPayload collision, MinecartVisualizerConfig config) {
            MutableComponent message = Component.literal("■ ").withColor(trackerColor.getHex())
                    .append(Component.literal("[" + shortUuid + "] ").withStyle(ChatFormatting.GRAY))
                    .append(Component.translatable("chat.minecartvisualizer.tracker.squeezed").withStyle(ChatFormatting.GOLD));

            if (config.printCollisionTarget) {
                MutableComponent target = Component.empty();
                if (collision.blockTarget()) {
                    target.append(Component.translatable("chat.minecartvisualizer.tracker.squeezed.block",
                            blockDisplayName(collision.targetId())));
                } else {
                    target.append(Component.translatable("chat.minecartvisualizer.tracker.squeezed.entity",
                            entityDisplayName(collision)));
                }
                message.append(Component.literal(" ")).append(target.withStyle(ChatFormatting.YELLOW));
            }

            if (config.printCollisionPosition) {
                Vec3 pos = collision.pos();
                String posStr = String.format("%.1f %.1f %.1f", pos.x, pos.y, pos.z);

                MutableComponent posText = Component.literal("\n  ").append(Component.translatable(
                        "chat.minecartvisualizer.tracker.squeezed.position",
                        String.format("(%.1f, %.1f, %.1f)", pos.x, pos.y, pos.z)));

                posText.withStyle(ChatFormatting.DARK_AQUA).withStyle(style -> style
                        .withClickEvent(new ClickEvent.SuggestCommand("/tp @s " + posStr))
                        .withHoverEvent(new HoverEvent.ShowText(
                                Component.translatable("chat.minecartvisualizer.tracker.tp_hover"))));

                message.append(posText);
            }

            Vec3 deltaVelocity = collision.deltaVelocity();
            if (config.printCollisionMomentum) {
                message.append(Component.literal("\n  ").append(Component.translatable(
                        "chat.minecartvisualizer.tracker.squeezed.momentum",
                        String.format("%.3f", deltaVelocity.length()),
                        String.format("(%.3f, %.3f, %.3f)",
                                deltaVelocity.x, deltaVelocity.y, deltaVelocity.z)
                ).withStyle(ChatFormatting.WHITE)));
            }

            if (config.printCollisionSpeedChange) {
                message.append(Component.literal("\n  ").append(Component.translatable(
                        "chat.minecartvisualizer.tracker.squeezed.speed",
                        String.format("%.3f", collision.speedBefore()),
                        String.format("%.3f", collision.speedAfter()),
                        String.format("%+.3f", collision.deltaSpeed())
                ).withStyle(ChatFormatting.WHITE)));
            }

            player.sendSystemMessage(message);
        }

        private Component blockDisplayName(String targetId) {
            Identifier id = Identifier.tryParse(targetId);
            if (id != null && BuiltInRegistries.BLOCK.containsKey(id)) {
                return BuiltInRegistries.BLOCK.getValue(id).getName();
            }
            return Component.literal(targetId);
        }

        private Component entityDisplayName(MinecartCollisionPayload collision) {
            Identifier id = Identifier.tryParse(collision.targetId());
            Component base = (id != null && BuiltInRegistries.ENTITY_TYPE.containsKey(id))
                    ? BuiltInRegistries.ENTITY_TYPE.getValue(id).getDescription()
                    : Component.literal(collision.targetId());

            String customName = collision.targetCustomName();
            if (customName != null && !customName.isEmpty()) {
                return Component.empty().append(base).append(Component.literal(" (" + customName + ")"));
            }
            return base;
        }

        private boolean hasInventoryChanged(List<ItemStack> currentInv) {
            if (lastInv == null) return false;
            for (int i = 0; i < 5; i++) {
                if (!ItemStack.matches(lastInv.get(i), currentInv.get(i))) {
                    return true;
                }
            }
            return false;
        }

        public void updateTrail(Vec3 newPoint, int maxPoints) {
            if (lastPoint == null) {
                lastPoint = newPoint;
                trailPoints.add(newPoint);
                return;
            }

            if (newPoint.distanceToSqr(lastPoint) < 0.0001) {
                return;
            }

            if (nextLastPoint != null && areCollinear(nextLastPoint, lastPoint, newPoint)) {
                trailPoints.pollLast();
                trailPoints.addLast(newPoint);
                lastPoint = newPoint;
            } else {
                trailPoints.add(newPoint);
                nextLastPoint = lastPoint;
                lastPoint = newPoint;

                while (trailPoints.size() > maxPoints) {
                    trailPoints.pollFirst();
                }
            }
        }

        //工具方法
        //检查向量共线
        public static boolean areCollinear(Vec3 a, Vec3 b, Vec3 c) {
            Vec3 v1 = b.subtract(a);
            Vec3 v2 = c.subtract(b);

            Vec3 cross = v1.cross(v2);
            double epsilon = 1e-6;

            return cross.lengthSqr() < epsilon && v1.dot(v2) > 0;
        }

        // 物品栏变动消息
        private void sendInventoryMessage(List<ItemStack> currentInv) {
            var config = MinecartVisualizerConfig.getInstance();
            if (preChangeInv == null) return;

            MutableComponent detailLines = Component.empty();
            boolean hasVisibleChange = false;

            for (int i = 0; i < 5; i++) {
                ItemStack oldItem = preChangeInv.get(i);
                ItemStack newItem = currentInv.get(i);
                int diff = newItem.getCount() - oldItem.getCount();

                if (diff == 0) continue;

                ItemStack item = diff > 0 ? newItem : oldItem;
                if (!shouldOutput(item)) continue;
                if (diff > 0 && !config.outputOnIncrease) continue;
                if (diff < 0 && !config.outputOnDecrease) continue;
                hasVisibleChange = true;

                if (config.printInventory){
                    detailLines.append(Component.literal("\n  ")
                            .append(Component.literal(diff > 0 ? "(+) " : "(-) ").withStyle(diff > 0 ? ChatFormatting.GREEN : ChatFormatting.RED))
                            .append(item.getItemName().copy().withStyle(ChatFormatting.WHITE))
                            .append(Component.literal(" x" + Math.abs(diff)).withStyle(ChatFormatting.GRAY))
                            .append(Component.translatable("chat.minecartvisualizer.tracker.slot", i + 1).withStyle(ChatFormatting.DARK_AQUA)));
                }
            }

            if (!hasVisibleChange) {
                resetTrackingState();
                return;
            }

            MutableComponent msg = Component.literal("■ ").withColor(trackerColor.getHex())
                    .append(Component.literal("[" + uuid.toString().substring(0, 4) + "] ").withStyle(ChatFormatting.GRAY))
                    .append(Component.translatable("chat.minecartvisualizer.tracker.collected", (lastChangeTick - firstChangeTick + 1)).withStyle(ChatFormatting.GOLD));

            if (config.printPosition){
                double dist = startPos.distanceTo(leastPos);
                MutableComponent posText;

                if (dist > 1) {
                    String moveStr = String.format(" (%.1f, %.1f -> %.1f, %.1f)",
                            startPos.x, startPos.z, leastPos.x, leastPos.z);
                    posText = Component.literal(moveStr);
                } else {
                    String atStr = String.format("(%.1f, %.1f)", leastPos.x, leastPos.z);
                    posText = Component.literal(atStr);
                }

                String tpCommand = String.format("/tp @s %.1f %.1f %.1f",
                        dist > 1 ? leastPos.x : startPos.x,
                        dist > 1 ? leastPos.y : startPos.y,
                        dist > 1 ? leastPos.z : startPos.z);

                posText.withStyle(ChatFormatting.DARK_AQUA)
                        .withStyle(style -> style
                                .withClickEvent(new ClickEvent.SuggestCommand(tpCommand))
                                .withHoverEvent(new HoverEvent.ShowText(
                                        Component.translatable("chat.minecartvisualizer.tracker.tp_hover")
                                ))
                        );

                msg.append(posText);
            }
            msg.append(detailLines);
            player.sendSystemMessage(msg);

            resetTrackingState();
        }

        //矿车摧毁（使用服务端补发的最终坐标与最终物品栏）
        private void sendDestroyedMessage(List<ItemStack> finalInv) {
            var config = MinecartVisualizerConfig.getInstance();
            MutableComponent message = Component.literal("■ ").withColor(trackerColor.getHex());

            message.append(Component.literal("[" + uuid.toString().substring(0, 4) + "] ").withStyle(ChatFormatting.GRAY));
            message.append(Component.translatable("chat.minecartvisualizer.tracker.removed").withStyle(ChatFormatting.RED));

            if (config.printPosition){
                if (leastPos != null) {
                    String posStr = String.format("%.1f %.1f %.1f", leastPos.x, leastPos.y, leastPos.z);

                    String displayStr = String.format("(%.1f, %.1f, %.1f)", leastPos.x, leastPos.y, leastPos.z);

                    message.append(Component.literal(displayStr)
                            .withStyle(ChatFormatting.WHITE, ChatFormatting.UNDERLINE)
                            .withStyle(style -> style
                                    .withClickEvent(new ClickEvent.SuggestCommand("/tp @s " + posStr))
                                    .withHoverEvent(new HoverEvent.ShowText(
                                            Component.translatable("chat.minecartvisualizer.tracker.tp_hover")
                                    ))
                            ));
                } else {
                    message.append(Component.translatable("chat.minecartvisualizer.tracker.unknown_location").withStyle(ChatFormatting.ITALIC));
                }
            }

            if (config.printInventory && finalInv != null && !finalInv.isEmpty()) {
                for (int i = 0; i < finalInv.size(); i++) {
                    ItemStack item = finalInv.get(i);
                    if (!item.isEmpty()) {
                        MutableComponent itemLine = Component.literal("\n  ")
                                .append(item.getItemName().copy().withStyle(ChatFormatting.WHITE))
                                .append(Component.literal(" x" + item.getCount()).withStyle(ChatFormatting.GRAY))
                                .append(Component.translatable("chat.minecartvisualizer.tracker.slot", i + 1).withStyle(ChatFormatting.DARK_AQUA));

                        message.append(itemLine);
                    }
                }
            }
            if (config.printDuration) {
                message.append(Component.literal("\n  ")).append(Component.translatable("chat.minecartvisualizer.tracker.duration", runTime).withStyle(ChatFormatting.GOLD));
            }

            player.sendSystemMessage(message);
        }

        private void recordCounterStats(List<ItemStack> items, RecordType type, List<ItemStack> previousItems) {
            if (!TrackersManager.counterIsEnable(trackerColor) || items == null) return;

            TrackerCounter counter = TrackersManager.getCounter(trackerColor);

            if (type == RecordType.INVENTORY_CHANGE && previousItems != null) {
                for (int i = 0; i < 5; i++) {
                    ItemStack current = items.get(i);
                    ItemStack old = previousItems.get(i);

                    if (current == null || old == null) continue;

                    int diff = current.getCount() - old.getCount();
                    if (diff == 0) continue;

                    if (ItemStack.isSameItem(old, current)) {
                        counter.addCounterData(current.getItemName(), diff, type);
                    } else {
                        if (!old.isEmpty()) counter.addCounterData(old.getItemName(), -old.getCount(), type);
                        if (!current.isEmpty()) counter.addCounterData(current.getItemName(), current.getCount(), type);
                    }
                }
            } else if (type == RecordType.DROPS) {
                for (ItemStack current : items) {
                    if (current != null && !current.isEmpty()) {
                        counter.addCounterData(current.getItemName(), current.getCount(), type);
                    }
                }
            }
        }

        private void resetTrackingState() {
            firstChangeTick = -1;
            preChangeInv = null;
            startPos = null;
        }

        private List<ItemStack> copyInventory(List<ItemStack> original) {
            List<ItemStack> copy = new ArrayList<>(original.size());
            for (ItemStack stack : original) {
                copy.add(stack.copy());
            }
            return copy;
        }

        private boolean shouldOutput(ItemStack stack) {
            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();

            if (filter.enableWhiteList) {
                return filter.whiteList.contains(itemId);
            }

            if (filter.enableBlackList) {
                return !filter.blackList.contains(itemId);
            }

            return true;
        }

        public TrackerColor getTrackerColor(){
            return trackerColor;
        }

        public String getShortUuid(){
            return shortUuid;
        }

        public Deque<Vec3> getTrailPoints() {
            return trailPoints;
        }

        public long getRunTime(){
            return runTime;
        }

        public boolean isRemoved() {
            return removed;
        }

        public enum RecordType {
            INVENTORY_CHANGE,
            DROPS
        }

    }
