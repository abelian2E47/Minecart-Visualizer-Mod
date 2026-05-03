package com.minecartvisualizer.tracker;

import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;

public class TrackerPointsManager {
    private static final TrackerPointsManager INSTANCE = new TrackerPointsManager();

    private static final Map<BlockPos, PointState> points = new HashMap<>();

    private TrackerPointsManager() {}

    public static TrackerPointsManager getInstance() {
        return INSTANCE;
    }

    public void addPoint(TrackerColor color, BlockPos pos) {
        points.put(pos, new PointState(color));
    }

    public void removePoint(TrackerColor color) {
        points.entrySet().removeIf(entry -> entry.getValue().getColor() == color);
    }

    public void removePoint(BlockPos pos) {
        points.remove(pos);
    }

    public void clearAllPoints() {
        points.clear();
    }

    public static Map<BlockPos, PointState> getPoints() {
        return points;
    }
}