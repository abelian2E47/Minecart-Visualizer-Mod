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

    /**
     * 按给定顺序（实际是服务端 x 坐标）排列组内矿车，并把领队设为第一台。
     *
     * <p>领队必须和 {@link #getMinecarts()} 的第一个元素一致：合并显示时物品栏取的是
     * 第一台矿车的数据，而文字/框体是否绘制看的是领队，两者不一致就会出现
     * "面板显示的是另一台矿车的物品"以及同一个堆叠画出多份面板。</p>
     */
    public void sort(Comparator<UUID> comparator) {
        minecarts.sort(comparator);
        //1.20.4 最低支持 Java 17，没有 List#getFirst()
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
