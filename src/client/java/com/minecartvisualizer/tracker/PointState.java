package com.minecartvisualizer.tracker;

public class PointState {
    private TrackerColor color;
    private Boolean active;

    public PointState(TrackerColor color){
        this.color = color;
        active = false;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public TrackerColor getColor() {
        return color;
    }

    public Boolean isActive() {
        return active;
    }
}
