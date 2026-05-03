package com.minecartvisualizer.tracker;

import net.minecraft.client.MinecraftClient;
import java.util.*;

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
        if (MinecraftClient.getInstance().player == null) return;

        if (trackers.containsKey(uuid)) {
            HopperMinecartTracker existingTracker = trackers.get(uuid);

            if (existingTracker.getTrackerColor() == color) {
                trackers.remove(uuid);
            } else {
                //颜色不同则更换追踪器
                trackers.put(uuid, new HopperMinecartTracker(
                        color, uuid, MinecraftClient.getInstance().player, entityId
                ));
            }
            return;
        }

        //新建追踪器
        trackers.put(uuid, new HopperMinecartTracker(
                color, uuid, MinecraftClient.getInstance().player, entityId
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

            //检查开关状态
            if (counter.isEnable()&&counter.isActive()) {
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


}
