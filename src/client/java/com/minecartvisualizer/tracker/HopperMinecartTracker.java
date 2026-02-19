    package com.minecartvisualizer.tracker;

    import com.minecartvisualizer.MinecartClientHandler;
    import com.minecartvisualizer.config.MinecartVisualizerConfig;
    import net.minecraft.client.network.ClientPlayerEntity;
    import net.minecraft.item.ItemStack;
    import net.minecraft.registry.Registries;
    import net.minecraft.text.ClickEvent;
    import net.minecraft.text.HoverEvent;
    import net.minecraft.text.MutableText;
    import net.minecraft.text.Text;
    import net.minecraft.util.Formatting;
    import net.minecraft.util.math.Vec3d;

    import java.util.*;
    import java.util.concurrent.ConcurrentLinkedDeque;

    public class HopperMinecartTracker {
        private final TrackerColor trackerColor;

        private final Deque<Vec3d> trailPoints = new ConcurrentLinkedDeque<>();
        private Vec3d lastPoint = null;
        private Vec3d nextLastPoint = null;

        private boolean removed = false;
        private final String shortUuid;
        private final UUID uuid;
        private final int id;
        private final ClientPlayerEntity player;
        private final long trackStartTime;
        private long runTime;
        private Vec3d leastPos = null;
        private List<ItemStack> lastInv = null;
        private final TrackerFilter filter;

        private long firstChangeTick = -1, lastChangeTick = -1;
        //开始连续吸取时物品栏
        private List<ItemStack> preChangeInv = null;
        //开始连续吸取时坐标
        private Vec3d startPos = null;

        public HopperMinecartTracker(TrackerColor trackerColor, UUID uuid, ClientPlayerEntity player, int id){
            this.id = id;
            this.uuid = uuid;
            this.shortUuid = uuid.toString().substring(0, 4);
            this.trackerColor = trackerColor;
            this.player = player;
            trackStartTime = player.clientWorld.getTime();
            this.filter = TrackersManager.filters.get(trackerColor);
            runTime = 0;
            this.tick();
        }

        public void tick() {
            var config = MinecartVisualizerConfig.getInstance();
            var minecartData = MinecartClientHandler.getMinecartData(uuid);
            var hopperData = MinecartClientHandler.getHopperMinecartData(uuid);
            long currentTime = player.clientWorld.getTime();
            runTime = currentTime - trackStartTime;

            if (player.clientWorld.getEntityById(id) == null || minecartData == null || hopperData == null) {
                if (firstChangeTick != -1) sendInventoryMessage(lastInv);
                //矿车摧毁行为
                if (leastPos != null) {
                    if (config.outputWhenDestroyed){
                        sendDestroyedMessage();
                    }
                    recordCounterStats(lastInv, RecordType.DROPS, null);
                    TrackersManager.getCounter(trackerColor).recordTrackerRemoval((int) (currentTime-trackStartTime));
                    this.removed = true;
                }
                return;
            }

            this.leastPos = minecartData.pos();
            List<ItemStack> currentInv = hopperData.items();

            Vec3d currentPos = minecartData.pos();

            //轨迹记录
            if (config.trackMinecartTrail){
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

        private boolean hasInventoryChanged(List<ItemStack> currentInv) {
            if (lastInv == null) return false;
            for (int i = 0; i < 5; i++) {
                if (!ItemStack.areEqual(lastInv.get(i), currentInv.get(i))) {
                    return true;
                }
            }
            return false;
        }

        public void updateTrail(Vec3d newPoint, int maxPoints) {
            if (lastPoint == null) {
                lastPoint = newPoint;
                trailPoints.add(newPoint);
                return;
            }

            if (newPoint.squaredDistanceTo(lastPoint) < 0.0001) {
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
        public static boolean areCollinear(Vec3d a, Vec3d b, Vec3d c) {
            Vec3d v1 = b.subtract(a);
            Vec3d v2 = c.subtract(b);

            Vec3d cross = v1.crossProduct(v2);
            double epsilon = 1e-6;

            return cross.lengthSquared() < epsilon && v1.dotProduct(v2) > 0;
        }

        // 物品栏变动消息
        private void sendInventoryMessage(List<ItemStack> currentInv) {
            var config = MinecartVisualizerConfig.getInstance();
            if (preChangeInv == null) return;

            MutableText detailLines = Text.empty();
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
                detailLines.append(Text.literal("\n  ")
                        .append(Text.literal(diff > 0 ? "(+) " : "(-) ").formatted(diff > 0 ? Formatting.GREEN : Formatting.RED))
                        .append(item.getItemName().copy().formatted(Formatting.WHITE))
                        .append(Text.literal(" x" + Math.abs(diff)).formatted(Formatting.GRAY))
                        .append(Text.translatable("chat.minecartvisualizer.tracker.slot", i + 1).formatted(Formatting.DARK_AQUA)));
                }
            }

            if (!hasVisibleChange) {
                resetTrackingState();
                return;
            }

            MutableText msg = Text.literal("■ ").withColor(trackerColor.getHex())
                    .append(Text.literal("[" + uuid.toString().substring(0, 4) + "] ").formatted(Formatting.GRAY))
                    .append(Text.translatable("chat.minecartvisualizer.tracker.collected", (lastChangeTick - firstChangeTick + 1)).formatted(Formatting.GOLD));

            if (config.printPosition) {
                double dist = startPos.distanceTo(leastPos);
                MutableText posText;

                if (dist > 1) {
                    String moveStr = String.format(" (%.1f, %.1f -> %.1f, %.1f)", startPos.x, startPos.z, leastPos.x, leastPos.z);
                    posText = Text.literal(moveStr);
                } else {
                    String atStr = String.format("(%.1f, %.1f)", leastPos.x, leastPos.z);
                    posText = Text.literal(atStr);
                }

                String tpCommand = String.format("/tp @s %.1f %.1f %.1f",
                        dist > 1 ? leastPos.x : startPos.x,
                        dist > 1 ? leastPos.y : startPos.y,
                        dist > 1 ? leastPos.z : startPos.z);

                posText.formatted(Formatting.DARK_AQUA)
                        .styled(style -> style
                                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, tpCommand))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.translatable("chat.minecartvisualizer.tracker.tp_hover"))));
                msg.append(posText);
            }
            msg.append(detailLines);
            player.sendMessage(msg, false);

            resetTrackingState();
        }

        //矿车摧毁
        private void sendDestroyedMessage() {
            var config = MinecartVisualizerConfig.getInstance();

            MutableText message = Text.literal("■ ").withColor(trackerColor.getHex());

            message.append(Text.literal("[" + uuid.toString().substring(0, 4) + "] ").formatted(Formatting.GRAY));
            message.append(Text.translatable("chat.minecartvisualizer.tracker.removed").formatted(Formatting.RED));

            if (config.printPosition) {
                if (leastPos != null) {
                    String posStr = String.format("%.1f %.1f %.1f", leastPos.x, leastPos.y, leastPos.z);
                    String displayStr = String.format("(%.1f, %.1f, %.1f)", leastPos.x, leastPos.y, leastPos.z);

                    message.append(Text.literal(" " + displayStr)
                            .formatted(Formatting.WHITE, Formatting.UNDERLINE)
                            .styled(style -> style
                                    .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/tp @s " + posStr))
                                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.translatable("chat.minecartvisualizer.tracker.tp_hover")))));
                } else {
                    message.append(Text.translatable("chat.minecartvisualizer.tracker.unknown_location").formatted(Formatting.ITALIC));
                }
            }

            if (config.printInventory && lastInv != null && !lastInv.isEmpty()) {
                for (int i = 0; i < lastInv.size(); i++) {
                    ItemStack item = lastInv.get(i);
                    if (!item.isEmpty()) {
                        MutableText itemLine = Text.literal("\n  ")
                                .append(item.getItemName().copy().formatted(Formatting.WHITE))
                                .append(Text.literal(" x" + item.getCount()).formatted(Formatting.GRAY))
                                .append(Text.translatable("chat.minecartvisualizer.tracker.slot", i + 1).formatted(Formatting.DARK_AQUA));

                        message.append(itemLine);
                    }
                }
            }
            if (config.printDuration) {
                message.append(Text.literal("\n  ")).append(Text.translatable("chat.minecartvisualizer.tracker.duration", runTime).formatted(Formatting.GOLD));
            }

            player.sendMessage(message, false);
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

                    if (ItemStack.areItemsEqual(old, current)) {
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
            String itemId = Registries.ITEM.getId(stack.getItem()).toString();

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

        public Deque<Vec3d> getTrailPoints() {
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