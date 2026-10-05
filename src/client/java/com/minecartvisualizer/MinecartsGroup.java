package com.minecartvisualizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class MinecartsGroup {
    private final List<UUID> minecarts = new ArrayList<>();
    private UUID leader;

    public MinecartsGroup(UUID leader){
        this.leader = leader;
        this.addMinecart(leader);
    }

    public void addMinecart(UUID uuid){
        minecarts.add(uuid);
    }

    public void sort(Comparator<UUID> comparator) {
        minecarts.sort(comparator);
        //1.20.x 最低支持 Java 17，没有 List#getFirst()
        leader = minecarts.get(0);
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
