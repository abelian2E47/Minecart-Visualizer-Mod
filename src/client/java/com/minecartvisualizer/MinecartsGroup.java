package com.minecartvisualizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class MinecartsGroup {
    private final List<UUID> minecarts = new ArrayList<>();
    private final UUID leader;

    public MinecartsGroup(UUID leader){
        this.leader = leader;
        this.addMinecart(leader);
    }

    public void addMinecart(UUID uuid){
        minecarts.add(uuid);
    }

    public void sort() {
        minecarts.sort(Comparator.comparing(UUID::toString));
    }

    public List<UUID> getMinecarts(){
        return minecarts;
    }

    public UUID getLeader(){
        return leader;
    }

    public int getSize(){
        return minecarts.size();
    }


}
