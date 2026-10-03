package com.minecartvisualizer.tracker;

import java.util.*;
import net.minecraft.client.Minecraft;

public class TrackersManager {
    private static final Map<UUID, HopperMinecartTracker> trackers = new HashMap<>();
    private static final Map<TrackerColor, TrackerCounter> counters = new HashMap<>();
    public static final Map<TrackerColor, TrackerFilter> filters = new EnumMap<>(TrackerColor.class);

    static {
        for (TrackerColor color : TrackerColor.values()) {
            filters.put(color, new TrackerFilter());
            counters.put(color, new TrackerCounter(color));
        }
    }

    public static void setTracker(UUID uuid, int entityId, TrackerColor color) {
        if (Minecraft.getInstance().player == null) return;

        if (trackers.containsKey(uuid)) {
            HopperMinecartTracker existingTracker = trackers.get(uuid);

            if (existingTracker.getTrackerColor() == color) {
                trackers.remove(uuid);
            } else {
                //颜色不同则更换追踪器
                trackers.put(uuid, new HopperMinecartTracker(
                        color, uuid, Minecraft.getInstance().player, entityId
                ));
            }
            return;
        }

        //新建追踪器
        trackers.put(uuid, new HopperMinecartTracker(
                color, uuid, Minecraft.getInstance().player, entityId
        ));
    }



    public static TrackerCounter getCounter(TrackerColor color){
        return counters.get(color);
    }

    public static TrackerColor getColorByUuid(UUID uuid) {
        HopperMinecartTracker tracker = trackers.get(uuid);
        return (tracker != null) ? tracker.getTrackerColor() : null;
    }

    public static HopperMinecartTracker getTracker(UUID uuid){
        return trackers.get(uuid);
    }

    public static boolean hasBeenTracked(UUID uuid){
        return trackers.containsKey(uuid);
    }

    public static boolean counterIsEnable(TrackerColor color){
        return counters.get(color).isEnable();
    }

    public static ArrayList<HopperMinecartTracker> getAllTrackers(){
        return new ArrayList<>(trackers.values());
    }

    public static boolean containsTracker(UUID uuid){
        return trackers.containsKey(uuid);
    }

    public static int getTrackerCount(TrackerColor color) {
        int count = 0;
        for (HopperMinecartTracker tracker : trackers.values()) {
            if (tracker.getTrackerColor() == color) {
                count++;
            }
        }
        return count;
    }

    public static void tickCounter() {
        //遍历所有颜色的计数器
        for (Map.Entry<TrackerColor, TrackerCounter> entry : counters.entrySet()) {
            TrackerCounter counter = entry.getValue();

            //检查开关状态。
            //注意这里不再要求 isActive()：isActive() 表示"当前物品栏里有东西"，
            //用它当计时门槛会让运行时长从第一次装到物品才开始算，/h 速率被整体放大。
            //运行时长应该从计数器启用那一刻开始按服务端 tick 累积。
            if (counter.isEnable()) {
                counter.tick();
            }
        }
    }

    public static void cleanInvalidTracker() {
        trackers.values().removeIf(tracker -> {
            tracker.tick();
            return tracker.isRemoved();
        });
    }

    public static void clearAll() {
        trackers.clear();
        for (TrackerCounter counter : counters.values()) {
            counter.reset();
        }
        for (TrackerFilter filter : filters.values()) {
            filter.clearWhiteList();
            filter.clearBlackList();
        }
    }


}
