package com.minecartvisualizer.tracker;

import java.util.ArrayList;
import java.util.List;

public class TrackerFilter {
    public List<String> whiteList = new ArrayList<>();
    public List<String> blackList = new ArrayList<>();
    public boolean enableWhiteList = false;
    public boolean enableBlackList = false;

    public void addWhiteList(String itemId) {
        whiteList.add(itemId);
    }

    public void addBlackList(String itemId) {
        blackList.add(itemId);
    }

    public void toggleWhiteList() {
        this.enableWhiteList = !this.enableWhiteList;
    }

    public void toggleBlackList() {
        this.enableBlackList = !this.enableBlackList;
    }

    public void removeWhiteList(String itemId) {
        whiteList.remove(itemId);
    }

    public void removeBlackList(String itemId) {
        blackList.remove(itemId);
    }

    public void clearWhiteList() {
        whiteList.clear();
    }

    public void clearBlackList() {
        blackList.clear();
    }
}
