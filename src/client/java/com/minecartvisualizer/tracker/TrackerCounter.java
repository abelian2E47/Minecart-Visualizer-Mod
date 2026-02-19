package com.minecartvisualizer.tracker;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.Map;

public class TrackerCounter {
    private final TrackerColor color;
    private final Map<Text, Integer> increase = new HashMap<>();
    private final Map<Text, Integer> decrease = new HashMap<>();
    private final Map<Text, Integer> destroyedDrops = new HashMap<>();
    private int runTime;
    private double avgLifetime;
    private int totalDestroyedTrackers;
    private boolean enable;

    public TrackerCounter(TrackerColor color){
        this.color = color;
        enable = true;
        runTime = 0;
        avgLifetime = 0;
    }

    public boolean isActive() {
        return !increase.isEmpty() || !decrease.isEmpty() || !destroyedDrops.isEmpty();
    }

    public void addCounterData(Text item, int count,HopperMinecartTracker.RecordType type) {
        switch (type) {
            case INVENTORY_CHANGE -> recordInventoryChange(item, count);
            case DROPS            -> recordDrops(item, count);
        }
    }

    public void recordInventoryChange(Text item, int deltaCount) {
        if (deltaCount > 0) {
            increase.merge(item, deltaCount, Integer::sum);
        } else if (deltaCount < 0) {
            decrease.merge(item, Math.abs(deltaCount), Integer::sum);
        }
    }

    public void recordDrops(Text item, int count) {
        destroyedDrops.merge(item, count, Integer::sum);
    }

    public void recordTrackerRemoval(int trackerRunTime) {
        if (!enable) return;

        totalDestroyedTrackers++;
        this.avgLifetime += (trackerRunTime - avgLifetime) / totalDestroyedTrackers;
    }

    public void printCounterReport(ClientPlayerEntity player) {
        MutableText report = Text.literal("\n=== Stats for ").append(Text.literal(color.toString()).withColor(color.getHex())).append(" ===\n");

        appendList(report, "Increase", increase);
        appendList(report, "Decrease", decrease);
        appendList(report, "Drops", destroyedDrops);

        report.append(Text.literal("-------------------------------\n").formatted(Formatting.DARK_GRAY));

        double runTimeMin = runTime / 1200.0;
        report.append(Text.literal("RunTime: ").formatted(Formatting.GRAY)
                .append(Text.literal(String.format("%.2f", runTimeMin)).formatted(Formatting.BLUE))
                .append(Text.literal(" min\n").formatted(Formatting.GRAY)));

        report.append(Text.literal("Avg Lifetime: ").formatted(Formatting.GRAY)
                .append(Text.literal(Math.round(avgLifetime) + " gt\n").formatted(Formatting.GOLD)));

        report.append(Text.literal("Tracker Count: ").formatted(Formatting.GRAY)
                .append(Text.literal(TrackersManager.getTrackerCount(color) + "").formatted(Formatting.LIGHT_PURPLE)));

        player.sendMessage(report, false);
    }

    private void appendList(MutableText report, String title, Map<Text, Integer> data) {
        if (data.isEmpty()) return;

        report.append(Text.literal(title + ":\n").formatted(Formatting.YELLOW));
        double hourlyFactor = (runTime > 0) ? 72000.0 / runTime : 0;

        data.forEach((nameText, total) -> {
            double iph = total * hourlyFactor;

            report.append(Text.literal("  - "))
                    .append(nameText)
                    .append(Text.literal(": ").formatted(Formatting.WHITE))
                    .append(Text.literal(total.toString()).formatted(Formatting.AQUA))
                    .append(Text.literal(String.format(", %.1f /h\n", iph)).formatted(Formatting.DARK_AQUA));
        });
    }

    public void reset(){
        runTime = 0;
        avgLifetime = 0;
        totalDestroyedTrackers = 0;
        increase.clear();
        decrease.clear();
        destroyedDrops.clear();
    }

    public void tick() {
        if (!enable) return;
        runTime++;
    }

    public void toggle(){
        enable = !enable;
    }

    public boolean isEnable(){
        return enable;
    }
}
