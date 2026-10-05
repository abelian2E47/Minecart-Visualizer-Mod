package com.minecartvisualizer;

import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 服务端每 tick 下发的漏斗矿车状态。
 *
 * <p>{@code items} 是服务端权威的物品栏；{@code extractionBlock} 与
 * {@code extractionEntities} 是服务端按原版 {@code HopperBlockEntity#extract} 逻辑
 * 算出的"这一 tick 真正会被吸取的对象"，客户端直接照着高亮即可，
 * 不再用客户端世界自己去猜（客户端区块/实体同步可能滞后，且远距离时根本没有区块数据）。</p>
 *
 * @param extractionBlock     会被吸取的容器方块（有则连形状一起高亮）
 * @param extractionEntities  会被吸取的实体容器碰撞箱（原版按此搜索并随机取一个）
 */
public record HopperMinecartDataPayload(UUID uuid, boolean enable, List<ItemStack> items,
                                        Optional<BlockPos> extractionBlock,
                                        List<Box> extractionEntities) {
    /** 兼容旧构造调用：只带物品栏（不携带吸取目标）。 */
    public HopperMinecartDataPayload(UUID uuid, boolean enable, List<ItemStack> items) {
        this(uuid, enable, items, Optional.empty(), List.of());
    }

    /**
     * 1.20.x 的 {@code PacketByteBuf#writeItemStack} / {@code readItemStack} 就是
     * 1.21.1 里 {@code ItemStack.OPTIONAL_PACKET_CODEC} 的等价物（同样把空堆编码成空堆）。
     */
    public static void write(PacketByteBuf buf, HopperMinecartDataPayload payload) {
        buf.writeUuid(payload.uuid());
        buf.writeBoolean(payload.enable());
        for (int i = 0; i < 5; i++) {
            buf.writeItemStack(payload.items().get(i));
        }

        buf.writeBoolean(payload.extractionBlock().isPresent());
        payload.extractionBlock().ifPresent(pos -> {
            buf.writeInt(pos.getX());
            buf.writeInt(pos.getY());
            buf.writeInt(pos.getZ());
        });

        buf.writeVarInt(payload.extractionEntities().size());
        for (Box box : payload.extractionEntities()) {
            buf.writeDouble(box.minX);
            buf.writeDouble(box.minY);
            buf.writeDouble(box.minZ);
            buf.writeDouble(box.maxX);
            buf.writeDouble(box.maxY);
            buf.writeDouble(box.maxZ);
        }
    }

    public static HopperMinecartDataPayload read(PacketByteBuf buf) {
        UUID uuid = buf.readUuid();
        boolean enable = buf.readBoolean();
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            items.add(buf.readItemStack());
        }

        Optional<BlockPos> extractionBlock = Optional.empty();
        if (buf.readBoolean()) {
            extractionBlock = Optional.of(new BlockPos(buf.readInt(), buf.readInt(), buf.readInt()));
        }

        int entityCount = buf.readVarInt();
        List<Box> extractionEntities = new ArrayList<>(entityCount);
        for (int i = 0; i < entityCount; i++) {
            extractionEntities.add(new Box(
                    buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readDouble(), buf.readDouble(), buf.readDouble()));
        }

        return new HopperMinecartDataPayload(uuid, enable, items, extractionBlock, extractionEntities);
    }
}
