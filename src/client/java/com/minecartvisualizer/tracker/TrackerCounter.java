package com.minecartvisualizer.tracker;

import com.minecartvisualizer.MinecartClientHandler;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public class TrackerCounter {
    private final TrackerColor color;
    private final Map<Component, Integer> increase = new HashMap<>();
    private final Map<Component, Integer> decrease = new HashMap<>();
    private final Map<Component, Integer> destroyedDrops = new HashMap<>();
    private int runTime;
    private double avgLifetime;
    private int totalDestroyedTrackers;
    private boolean enable;
    //上一次统计到的服务端时间，用于按服务端 tick 累计运行时长
    private long lastServerTime = -1L;

    public TrackerCounter(TrackerColor color){
        this.color = color;
        enable = true;
        runTime = 0;
        avgLifetime = 0;
    }

    public boolean isActive() {
        return !increase.isEmpty() || !decrease.isEmpty() || !destroyedDrops.isEmpty();
    }

    public void addCounterData(Component item, int count,HopperMinecartTracker.RecordType type) {
        switch (type) {
            case INVENTORY_CHANGE -> recordInventoryChange(item, count);
            case DROPS            -> recordDrops(item, count);
        }
    }

    public void recordInventoryChange(Component item, int deltaCount) {
        if (deltaCount > 0) {
            increase.merge(item, deltaCount, Integer::sum);
        } else if (deltaCount < 0) {
            decrease.merge(item, Math.abs(deltaCount), Integer::sum);
        }
    }

    public void recordDrops(Component item, int count) {
        destroyedDrops.merge(item, count, Integer::sum);
    }

    public void recordTrackerRemoval(int trackerRunTime) {
        if (!enable) return;

        totalDestroyedTrackers++;
        this.avgLifetime += (trackerRunTime - avgLifetime) / totalDestroyedTrackers;
    }

    public void printCounterReport(LocalPlayer player) {
        MutableComponent report = Component.literal("\n=== Stats for ").append(Component.literal(color.toString()).withColor(color.getHex())).append(" ===\n");

        appendList(report, "Increase", increase);
        appendList(report, "Decrease", decrease);
        appendList(report, "Drops", destroyedDrops);

        report.append(Component.literal("-------------------------------\n").withStyle(ChatFormatting.DARK_GRAY));

        double runTimeMin = runTime / 1200.0;
        report.append(Component.literal("RunTime: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.format("%.2f", runTimeMin)).withStyle(ChatFormatting.BLUE))
                .append(Component.literal(" min\n").withStyle(ChatFormatting.GRAY)));

        report.append(Component.literal("Avg Lifetime: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(Math.round(avgLifetime) + " gt\n").withStyle(ChatFormatting.GOLD)));

        report.append(Component.literal("Minecart Count: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(TrackersManager.getTrackerCount(color) + "").withStyle(ChatFormatting.LIGHT_PURPLE)));

        player.sendSystemMessage(report);
    }

    private void appendList(MutableComponent report, String title, Map<Component, Integer> data) {
        if (data.isEmpty()) return;

        report.append(Component.literal(title + ":\n").withStyle(ChatFormatting.YELLOW));
        double hourlyFactor = (runTime > 0) ? 72000.0 / runTime : 0;

        data.forEach((nameText, total) -> {
            double iph = total * hourlyFactor;

            report.append(Component.literal("  - "))
                    .append(nameText)
                    .append(Component.literal(": ").withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(total.toString()).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(String.format(", %.1f /h\n", iph)).withStyle(ChatFormatting.DARK_AQUA));
        });
    }

    public void reset(){
        runTime = 0;
        avgLifetime = 0;
        totalDestroyedTrackers = 0;
        lastServerTime = -1L;
        increase.clear();
        decrease.clear();
        destroyedDrops.clear();
    }

    public void tick() {
        if (!enable) return;
        long serverTime = MinecartClientHandler.getLatestServerTime();
        if (serverTime < 0) return;

        if (lastServerTime >= 0 && serverTime > lastServerTime) {
            long delta = serverTime - lastServerTime;
            runTime += (int) Math.min(delta, Integer.MAX_VALUE - (long) runTime);
        }
        lastServerTime = serverTime;
    }

    public void toggle(){
        enable = !enable;
    }

    public boolean isEnable(){
        return enable;
    }
}
